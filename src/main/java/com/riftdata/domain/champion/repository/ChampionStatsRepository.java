package com.riftdata.domain.champion.repository;

import com.riftdata.global.common.Position;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 챔피언 통계(API-19, 20)에 필요한 픽·밴 원시 값. 2026 시즌(정규 시즌·플레이오프)만 대상이다. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChampionStatsRepository {

	private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
	private static final LocalDateTime END = LocalDateTime.of(2027, 1, 1, 0, 0);

	private final EntityManager entityManager;

	/** 패치 조건에 맞는 세트 수 (픽밴률의 분모) */
	public long countSets(String patch) {
		StringBuilder jpql = new StringBuilder("""
				select count(ms.id) from MatchSet ms join ms.match m
				where m.matchTime >= :start and m.matchTime < :end
				""");
		if (patch != null) jpql.append(" and ms.version = :patch");
		TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class)
				.setParameter("start", START).setParameter("end", END);
		if (patch != null) query.setParameter("patch", patch);
		return query.getSingleResult();
	}

	/** 선수 픽 한 건씩. championId가 있으면 그 챔피언만 */
	public List<Pick> findPicks(String patch, Position position, Long championId) {
		StringBuilder jpql = new StringBuilder("""
				select c.id as championId, c.name as championName, ps.position as position,
				       p.id as playerId, p.nickname as nickname, t.code as teamCode,
				       ts.victory as win, ps.kills as kills, ps.deaths as deaths, ps.assists as assists,
				       ps.totalPoints as totalPoints
				from PlayerSet ps join ps.champion c join ps.player p join p.team t
				join ps.teamSet ts join ts.matchSet ms join ms.match m
				where m.matchTime >= :start and m.matchTime < :end
				""");
		if (patch != null) jpql.append(" and ms.version = :patch");
		if (position != null) jpql.append(" and ps.position = :position");
		if (championId != null) jpql.append(" and c.id = :championId");
		TypedQuery<Tuple> query = entityManager.createQuery(jpql.toString(), Tuple.class)
				.setParameter("start", START).setParameter("end", END);
		if (patch != null) query.setParameter("patch", patch);
		if (position != null) query.setParameter("position", position);
		if (championId != null) query.setParameter("championId", championId);
		return query.getResultList().stream().map(row -> new Pick(
				row.get("championId", Long.class), row.get("championName", String.class),
				row.get("position", Position.class), row.get("playerId", Long.class),
				row.get("nickname", String.class), row.get("teamCode", String.class),
				Boolean.TRUE.equals(row.get("win", Boolean.class)),
				((Number) row.get("kills")).intValue(), ((Number) row.get("deaths")).intValue(),
				((Number) row.get("assists")).intValue(), row.get("totalPoints", Integer.class))).toList();
	}

	/** 챔피언별 밴 수. 밴에는 포지션이 없다. */
	public List<BanCount> countBans(String patch) {
		StringBuilder jpql = new StringBuilder("""
				select c.id as championId, c.name as championName, count(b.id) as bans
				from Ban b join b.champion c join b.teamSet ts join ts.matchSet ms join ms.match m
				where m.matchTime >= :start and m.matchTime < :end
				""");
		if (patch != null) jpql.append(" and ms.version = :patch");
		jpql.append(" group by c.id, c.name");
		TypedQuery<Tuple> query = entityManager.createQuery(jpql.toString(), Tuple.class)
				.setParameter("start", START).setParameter("end", END);
		if (patch != null) query.setParameter("patch", patch);
		return query.getResultList().stream().map(row -> new BanCount(row.get("championId", Long.class),
				row.get("championName", String.class), ((Number) row.get("bans")).longValue())).toList();
	}

	/** @param teamCode 선수의 현재 소속 팀 */
	public record Pick(Long championId, String championName, Position position, Long playerId, String nickname,
			String teamCode, boolean win, int kills, int deaths, int assists, Integer totalPoints) {
	}

	public record BanCount(Long championId, String championName, long bans) {
	}
}
