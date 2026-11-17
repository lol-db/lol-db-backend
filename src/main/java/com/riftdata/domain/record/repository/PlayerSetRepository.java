package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.dto.PlayerSetPoints;
import com.riftdata.domain.record.dto.SetPog;
import com.riftdata.domain.record.entity.PlayerSet;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerSetRepository extends JpaRepository<PlayerSet, Long> {

	/** 선수별 세트 기록 (ix_ps_player) */
	List<PlayerSet> findByPlayerId(Long playerId);

	/** 챔피언 통계 (ix_ps_champion) */
	List<PlayerSet> findByChampionId(Long championId);

	/** 팀세트기록 하나의 선수 5명 */
	List<PlayerSet> findByTeamSetId(Long teamSetId);

	/** 세트 하나의 선수 10명 */
	List<PlayerSet> findByTeamSetMatchSetId(Long setId);

	/**
	 * 여러 경기의 세트별 POG를 한 번에 읽는다 (경기 목록·홈·경기 상세).
	 * POG는 B3 계산 후에 채워지므로, 계산 전이면 그 세트는 결과에 없다.
	 */
	@Query("""
			select new com.riftdata.domain.record.dto.SetPog(
				s.match.id, s.setNumber, ts.team.id, p.id, p.nickname, c.id, c.name, ps.totalPoints)
			from PlayerSet ps
			join ps.teamSet ts
			join ts.matchSet s
			join ps.player p
			join ps.champion c
			where ps.pog = true and s.match.id in :matchIds
			order by s.match.id, s.setNumber
			""")
	List<SetPog> findSetPogs(@Param("matchIds") Collection<Long> matchIds);

	/** 세트 상세 스코어보드 (API-07). 세트의 선수 10명을 선수·챔피언·팀세트기록과 함께 읽는다. */
	@Query("""
			select ps from PlayerSet ps
			join fetch ps.teamSet ts
			join fetch ps.player
			join fetch ps.champion
			where ts.matchSet.id = :setId
			""")
	List<PlayerSet> findScoreboardByMatchSetId(@Param("setId") Long setId);

	/** 경기 하나의 모든 선수 세트 기록 (경기 상세의 시리즈 선수 평점) */
	@Query("""
			select new com.riftdata.domain.record.dto.PlayerSetPoints(
				s.setNumber, ts.team.id, p.id, p.nickname, ps.position, ps.totalPoints)
			from PlayerSet ps
			join ps.teamSet ts
			join ts.matchSet s
			join ps.player p
			where s.match.id = :matchId
			order by s.setNumber
			""")
	List<PlayerSetPoints> findPlayerSetPoints(@Param("matchId") Long matchId);
}
