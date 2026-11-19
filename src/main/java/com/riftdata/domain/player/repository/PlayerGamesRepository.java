package com.riftdata.domain.player.repository;

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

/** 선수 경기 기록에 필요한 값과 경기 전체 점수를 일괄 조회합니다. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerGamesRepository {
	private static final LocalDateTime SEASON_START = LocalDateTime.of(2026, 1, 1, 0, 0);
	private static final LocalDateTime SEASON_END = LocalDateTime.of(2027, 1, 1, 0, 0);
	private final EntityManager entityManager;

	/** 조건은 선수 세트에 적용하며, 맞라이너 기록에는 챔피언 조건을 적용하지 않습니다. */
	public List<GameRow> findGames(Long playerId, Boolean win, Long opponentTeamId,
			String patch, Long championId) {
		StringBuilder jpql = new StringBuilder("""
				select m.id as matchId, m.matchTime as playedAt,
				       st.id as stageId, st.name as stageName, st.regular as regular,
				       ts.team.id as ownTeamId, t1.id as team1Id, t1.code as team1Code,
				       t2.id as team2Id, t2.code as team2Code,
				       ms.setNumber as setNo, ms.playTime as playTime,
				       c.id as championId, c.name as championName,
				       ts.victory as win, ps.kills as kills, ps.deaths as deaths,
				       ps.assists as assists, ps.minionKills + ps.monsterKills as cs,
				       ps.damageToChampions as damage, ps.goldAt15 as goldAt15,
				       opponent.goldAt15 as opponentGoldAt15,
				       ps.totalPoints as totalPoints, ps.pog as pog
				from PlayerSet ps
				join ps.teamSet ts join ts.matchSet ms join ms.match m
				join m.stage st join m.team1 t1 join m.team2 t2 join ps.champion c
				left join TeamSet opposingTeam on opposingTeam.matchSet = ms and opposingTeam.team <> ts.team
				left join PlayerSet opponent on opponent.teamSet = opposingTeam and opponent.position = ps.position
				where ps.player.id = :playerId
				  and m.matchTime >= :seasonStart and m.matchTime < :seasonEnd
				""");
		if (win != null) jpql.append(" and ts.victory = :win");
		if (opponentTeamId != null) jpql.append("""
				 and ((ts.team = t1 and t2.id = :opponentTeamId)
				   or (ts.team = t2 and t1.id = :opponentTeamId))
				""");
		if (patch != null) jpql.append(" and ms.version = :patch");
		if (championId != null) jpql.append(" and c.id = :championId");
		jpql.append(" order by m.matchTime desc, m.id desc, ms.setNumber asc");
		TypedQuery<Tuple> query = entityManager.createQuery(jpql.toString(), Tuple.class)
				.setParameter("playerId", playerId)
				.setParameter("seasonStart", SEASON_START).setParameter("seasonEnd", SEASON_END);
		if (win != null) query.setParameter("win", win);
		if (opponentTeamId != null) query.setParameter("opponentTeamId", opponentTeamId);
		if (patch != null) query.setParameter("patch", patch);
		if (championId != null) query.setParameter("championId", championId);
		return query.getResultList().stream().map(row -> new GameRow(
				row.get("matchId", Long.class), row.get("playedAt", LocalDateTime.class),
				row.get("stageId", Long.class), row.get("stageName", String.class), row.get("regular", Boolean.class),
				row.get("ownTeamId", Long.class), row.get("team1Id", Long.class), row.get("team1Code", String.class),
				row.get("team2Id", Long.class), row.get("team2Code", String.class),
				number(row, "setNo"), row.get("playTime", LocalTime.class).toSecondOfDay(),
				row.get("championId", Long.class), row.get("championName", String.class), row.get("win", Boolean.class),
				number(row, "kills"), number(row, "deaths"), number(row, "assists"), number(row, "cs"),
				number(row, "damage"), row.get("goldAt15", Integer.class), row.get("opponentGoldAt15", Integer.class),
				row.get("totalPoints", Integer.class), row.get("pog", Boolean.class))).toList();
	}

	/** 세트 필터와 출전 선수에 관계없이 해당 경기의 전체 팀 승수를 읽습니다. */
	public Map<MatchTeam, Integer> findSeriesWins(Collection<Long> matchIds) {
		if (matchIds.isEmpty()) return Map.of();
		List<Tuple> rows = entityManager.createQuery("""
				select ts.matchSet.match.id as matchId, ts.team.id as teamId,
				       sum(case when ts.victory = true then 1 else 0 end) as wins
				from TeamSet ts
				where ts.matchSet.match.id in :matchIds
				group by ts.matchSet.match.id, ts.team.id
				""", Tuple.class).setParameter("matchIds", matchIds).getResultList();
		Map<MatchTeam, Integer> result = new HashMap<>();
		for (Tuple row : rows) {
			result.put(new MatchTeam(row.get("matchId", Long.class), row.get("teamId", Long.class)),
					number(row, "wins"));
		}
		return result;
	}

	private static int number(Tuple row, String field) {
		return ((Number) row.get(field)).intValue();
	}

	public record MatchTeam(Long matchId, Long teamId) {
	}

	public record GameRow(
			Long matchId, LocalDateTime playedAt, Long stageId, String stageName, boolean regular,
			Long ownTeamId, Long team1Id, String team1Code, Long team2Id, String team2Code,
			int setNo, int durationSec, Long championId, String championName, boolean win,
			int kills, int deaths, int assists, int cs, int damage, Integer goldAt15,
			Integer opponentGoldAt15, Integer totalPoints, boolean pog) {
	}
}
