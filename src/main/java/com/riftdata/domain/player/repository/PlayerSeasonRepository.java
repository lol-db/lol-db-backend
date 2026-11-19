package com.riftdata.domain.player.repository;

import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.global.common.Position;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerSeasonRepository extends JpaRepository<PlayerSeason, Long> {

	Optional<PlayerSeason> findByPlayerId(Long playerId);

	/** 요청한 선수들의 등급에 필요한 값을 한 번에 조회합니다. */
	@Query("""
			select p.id as playerId, p.nickname as nickname, p.position as position,
			       t.code as teamCode, s.totalPoints as totalPoints
			from PlayerSeason s join s.player p join p.team t
			where s.sets >= 10 and s.totalPoints between 0 and 100
			  and p.id in :playerIds
			""")
	List<SeasonPlayer> findGradeCandidatesByPlayerIds(@Param("playerIds") Collection<Long> playerIds);

	/** 단일 2026 시즌의 포지션별 1위 후보를 조회하며, 점수 미적재 선수는 제외합니다. */
	@Query("""
			select p.id as playerId, p.nickname as nickname, p.position as position,
			       t.code as teamCode, s.totalPoints as totalPoints
			from PlayerSeason s join s.player p join p.team t
			where s.sets >= 10 and s.totalPoints between 0 and 100
			""")
	List<SeasonPlayer> findGradeCandidates();

	/** 추가 조회 없이 홈 응답을 만들기 위한 선수와 시즌 점수의 조회 결과입니다. */
	interface SeasonPlayer {
		Long getPlayerId();
		String getNickname();
		Position getPosition();
		String getTeamCode();
		Integer getTotalPoints();
	}
}
