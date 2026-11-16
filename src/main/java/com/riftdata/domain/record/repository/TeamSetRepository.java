package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.dto.SetWinner;
import com.riftdata.domain.record.entity.TeamSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamSetRepository extends JpaRepository<TeamSet, Long> {

	/** 세트의 양 팀 기록 (2행) */
	List<TeamSet> findByMatchSetId(Long setId);

	Optional<TeamSet> findByMatchSetIdAndTeamId(Long setId, Long teamId);

	List<TeamSet> findByTeamId(Long teamId);

	/**
	 * 여러 경기의 세트별 승리 팀을 한 번에 읽는다 (경기 목록·홈·경기 상세의 세트 스코어).
	 * 경기마다 따로 조회하지 않도록(N+1) matchId 목록으로 받는다. 세트 기록이 없는 경기(예정)는 결과에 없다.
	 */
	@Query("""
			select new com.riftdata.domain.record.dto.SetWinner(s.match.id, s.setNumber, ts.team.id)
			from TeamSet ts
			join ts.matchSet s
			where ts.victory = true and s.match.id in :matchIds
			order by s.match.id, s.setNumber
			""")
	List<SetWinner> findSetWinners(@Param("matchIds") Collection<Long> matchIds);
}
