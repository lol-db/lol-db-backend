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

/** 선수 통계에 필요한 원시 값만 조회하며, 정규 경기와 플레이오프를 모두 포함합니다. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerStatisticsRepository {

	private static final LocalDateTime SEASON_START = LocalDateTime.of(2026, 1, 1, 0, 0);
	private static final LocalDateTime SEASON_END = LocalDateTime.of(2027, 1, 1, 0, 0);

	private final EntityManager entityManager;

	/** 지정한 선수의 2026년 세트 기록을 조회합니다. 필터가 null이면 해당 조건을 적용하지 않습니다. */
	public List<SetRecord> findRecords(Long playerId, String patch, Long championId) {
		TypedQuery<Tuple> query = createSeasonalQuery("""
				select ts.id as teamSetId, m.matchTime as playedAt, ms.playTime as playTime,
				       ts.victory as win, ps.kills as kills, ps.deaths as deaths,
				       ps.assists as assists, ps.minionKills + ps.monsterKills as cs,
				       ps.totalPoints as totalPoints, ps.pog as pog
				from PlayerSet ps
				join ps.teamSet ts
				join ts.matchSet ms
				join ms.match m
				where ps.player.id = :playerId
				""", patch, championId);
		query.setParameter("playerId", playerId);

		return query.getResultList().stream()
				.map(row -> new SetRecord(
						row.get("teamSetId", Long.class),
						row.get("playedAt", LocalDateTime.class),
						row.get("playTime", LocalTime.class).toSecondOfDay(),
						row.get("win", Boolean.class),
						((Number) row.get("kills")).intValue(),
						((Number) row.get("deaths")).intValue(),
						((Number) row.get("assists")).intValue(),
						((Number) row.get("cs")).intValue(),
						row.get("totalPoints", Integer.class),
						row.get("pog", Boolean.class)))
				.toList();
	}

	/** 대상 세트의 팀원 전체 킬을 합산합니다. 선수의 챔피언 필터를 팀원에게 적용하지 않습니다. */
	public Map<Long, Long> findTeamKills(Collection<Long> teamSetIds) {
		if (teamSetIds.isEmpty()) {
			return Map.of();
		}

		List<Tuple> rows = entityManager.createQuery("""
				select ps.teamSet.id as teamSetId, sum(ps.kills) as teamKills
				from PlayerSet ps
				where ps.teamSet.id in :teamSetIds
				group by ps.teamSet.id
				""", Tuple.class)
				.setParameter("teamSetIds", teamSetIds)
				.getResultList();
		Map<Long, Long> teamKills = new HashMap<>();
		for (Tuple row : rows) {
			teamKills.put(row.get("teamSetId", Long.class), ((Number) row.get("teamKills")).longValue());
		}
		return teamKills;
	}

	/** 같은 포지션의 2026년 기록에서 CS와 경기시간만 조회하여 비교 기준을 제공합니다. */
	public List<CsSample> findPositionCsSamples(Position position, String patch, Long championId) {
		TypedQuery<Tuple> query = createSeasonalQuery("""
				select ps.minionKills + ps.monsterKills as cs, ms.playTime as playTime
				from PlayerSet ps
				join ps.teamSet ts
				join ts.matchSet ms
				join ms.match m
				where ps.position = :position
				""", patch, championId);
		query.setParameter("position", position);

		return query.getResultList().stream()
				.map(row -> new CsSample(
						((Number) row.get("cs")).intValue(),
						row.get("playTime", LocalTime.class).toSecondOfDay()))
				.toList();
	}

	/** 선수 기록과 포지션 비교 자료에 같은 시즌 및 선택 필터를 적용합니다. */
	private TypedQuery<Tuple> createSeasonalQuery(String baseQuery, String patch, Long championId) {
		StringBuilder jpql = new StringBuilder(baseQuery)
				.append(" and m.matchTime >= :seasonStart and m.matchTime < :seasonEnd");
		if (patch != null) {
			jpql.append(" and ms.version = :patch");
		}
		if (championId != null) {
			jpql.append(" and ps.champion.id = :championId");
		}

		TypedQuery<Tuple> query = entityManager.createQuery(jpql.toString(), Tuple.class)
				.setParameter("seasonStart", SEASON_START)
				.setParameter("seasonEnd", SEASON_END);
		if (patch != null) {
			query.setParameter("patch", patch);
		}
		if (championId != null) {
			query.setParameter("championId", championId);
		}
		return query;
	}

	public record SetRecord(Long teamSetId, LocalDateTime playedAt, int durationSeconds,
			boolean win, int kills, int deaths, int assists, int cs, Integer totalPoints, boolean pog) {
	}

	public record CsSample(int cs, int durationSeconds) {
	}
}
