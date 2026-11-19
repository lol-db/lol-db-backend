package com.riftdata.domain.player.repository;

import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 선수 랭킹에 필요한 선수, 시즌 점수, 세트 점수를 각각 일괄 조회합니다. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerRankingRepository {
	private final EntityManager em;

	public List<Player> findPlayers() {
		return em.createQuery("select p from Player p join fetch p.team", Player.class).getResultList();
	}

	public List<PlayerSeason> findSeasons() {
		return em.createQuery("select s from PlayerSeason s join fetch s.player", PlayerSeason.class)
				.getResultList();
	}

	/** 평점이 아직 없는 세트도 출전 수에 포함하므로 점수가 null인 행도 가져옵니다. */
	public List<SetRating> findSeasonSetRatings() {
		return em.createQuery("""
				select ps.player.id as playerId, ps.totalPoints as points
				from PlayerSet ps join ps.teamSet ts join ts.matchSet ms join ms.match m
				where m.matchTime >= :start and m.matchTime < :end
				""", Tuple.class)
				.setParameter("start", LocalDateTime.of(2026, 1, 1, 0, 0))
				.setParameter("end", LocalDateTime.of(2027, 1, 1, 0, 0))
				.getResultList().stream()
				.map(row -> new SetRating(row.get("playerId", Long.class), row.get("points", Integer.class)))
				.toList();
	}

	public record SetRating(Long playerId, Integer points) {
	}
}
