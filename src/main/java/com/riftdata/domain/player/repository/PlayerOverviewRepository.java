package com.riftdata.domain.player.repository;

import com.riftdata.global.common.Position;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 종합 탭의 비교 집단과 세트별 계산에 필요한 원시 값만 일괄 조회합니다. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerOverviewRepository {
	private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
	private static final LocalDateTime END = LocalDateTime.of(2027, 1, 1, 0, 0);
	private final EntityManager entityManager;

	/** 현재 주포지션이 같은 선수들의 세트 기록을 조회하며 필터는 대상 선수와 비교 집단에 같이 적용합니다. */
	public List<OverviewSet> findPositionSets(Position position, String patch, Long championId) {
		StringBuilder jpql = new StringBuilder("""
				select p.id as playerId, ts.id as teamSetId, m.id as matchId,
				       m.matchTime as playedAt, ms.setNumber as setNo, ms.playTime as playTime,
				       c.id as championId, c.name as championName, ts.victory as win,
				       case when ts.team.id = m.team1.id then m.team2.id else m.team1.id end as opponentId,
				       case when ts.team.id = m.team1.id then m.team2.code else m.team1.code end as opponentCode,
				       ps.kills as kills, ps.deaths as deaths, ps.assists as assists,
				       ps.minionKills + ps.monsterKills as cs, ps.damageToChampions as damage,
				       ps.earnedGolds as earnedGold, ps.visionPoints as vision,
				       ps.goldAt15 as gold15, enemy.goldAt15 as enemyGold15,
				       ps.csAt15 as cs15, enemy.csAt15 as enemyCs15,
				       ps.xpAt15 as xp15, enemy.xpAt15 as enemyXp15,
				       ps.totalPoints as totalPoints, ps.pog as pog
				from PlayerSet ps join ps.player p join ps.teamSet ts
				join ts.matchSet ms join ms.match m join ps.champion c
				left join PlayerSet enemy on enemy.teamSet.matchSet.id = ms.id
				  and enemy.teamSet.team.id <> ts.team.id and enemy.position = ps.position
				where p.position = :position and m.matchTime >= :start and m.matchTime < :end
				""");
		if (patch != null) jpql.append(" and ms.version = :patch");
		if (championId != null) jpql.append(" and c.id = :championId");
		TypedQuery<Tuple> query = entityManager.createQuery(jpql.toString(), Tuple.class)
				.setParameter("position", position).setParameter("start", START).setParameter("end", END);
		if (patch != null) query.setParameter("patch", patch);
		if (championId != null) query.setParameter("championId", championId);
		return query.getResultList().stream().map(row -> new OverviewSet(
				row.get("playerId", Long.class), row.get("teamSetId", Long.class),
				row.get("matchId", Long.class), row.get("playedAt", LocalDateTime.class),
				number(row, "setNo"), row.get("playTime", LocalTime.class).toSecondOfDay(),
				row.get("championId", Long.class), row.get("championName", String.class),
				row.get("opponentId", Long.class), row.get("opponentCode", String.class),
				row.get("win", Boolean.class), number(row, "kills"), number(row, "deaths"),
				number(row, "assists"), number(row, "cs"), number(row, "damage"),
				number(row, "earnedGold"), number(row, "vision"),
				difference(row, "gold15", "enemyGold15"), difference(row, "cs15", "enemyCs15"),
				difference(row, "xp15", "enemyXp15"), row.get("totalPoints", Integer.class),
				row.get("pog", Boolean.class))).toList();
	}

	/** 필터와 관계없는 실제 시즌 출전 수로 10세트 이상 비교 집단을 정합니다. */
	public Map<Long, Long> findPositionSetCounts(Position position) {
		List<Tuple> rows = entityManager.createQuery("""
				select ps.player.id as playerId, count(ps.id) as sets
				from PlayerSet ps
				where ps.player.position = :position
				  and ps.teamSet.matchSet.match.matchTime >= :start
				  and ps.teamSet.matchSet.match.matchTime < :end
				group by ps.player.id
				""", Tuple.class).setParameter("position", position)
				.setParameter("start", START).setParameter("end", END).getResultList();
		Map<Long, Long> counts = new HashMap<>();
		for (Tuple row : rows) counts.put(row.get("playerId", Long.class), ((Number) row.get("sets")).longValue());
		return counts;
	}

	/** 챔피언 필터가 걸려도 당시 팀원 전체의 킬과 피해량을 분모로 사용합니다. */
	public Map<Long, TeamTotals> findTeamTotals(Collection<Long> teamSetIds) {
		if (teamSetIds.isEmpty()) return Map.of();
		List<Tuple> rows = entityManager.createQuery("""
				select ps.teamSet.id as teamSetId, sum(ps.kills) as kills, sum(ps.damageToChampions) as damage
				from PlayerSet ps where ps.teamSet.id in :teamSetIds group by ps.teamSet.id
				""", Tuple.class).setParameter("teamSetIds", teamSetIds).getResultList();
		Map<Long, TeamTotals> totals = new HashMap<>();
		for (Tuple row : rows) totals.put(row.get("teamSetId", Long.class),
				new TeamTotals(((Number) row.get("kills")).longValue(), ((Number) row.get("damage")).longValue()));
		return totals;
	}

	private static int number(Tuple row, String alias) {
		return ((Number) row.get(alias)).intValue();
	}

	private static Double difference(Tuple row, String own, String other) {
		Number left = (Number) row.get(own);
		Number right = (Number) row.get(other);
		return left == null || right == null ? null : left.doubleValue() - right.doubleValue();
	}

	public record TeamTotals(long kills, long damage) {
	}
	public record OverviewSet(Long playerId, Long teamSetId, Long matchId, LocalDateTime playedAt,
			int setNo, int durationSec, Long championId, String championName, Long opponentId,
			String opponentCode, boolean win, int kills, int deaths, int assists, int cs, int damage,
			int earnedGold, int vision, Double goldDiff15, Double csDiff15, Double xpDiff15,
			Integer totalPoints, boolean pog) {
	}
}
