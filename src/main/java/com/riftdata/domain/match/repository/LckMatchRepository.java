package com.riftdata.domain.match.repository;

import com.riftdata.domain.match.entity.LckMatch;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LckMatchRepository extends JpaRepository<LckMatch, Long> {

	/** 날짜별 일정·결과 (SCR-002, ix_match_time) */
	List<LckMatch> findByMatchTimeBetweenOrderByMatchTimeAsc(LocalDateTime from, LocalDateTime to);

	List<LckMatch> findByStageIdOrderByMatchTimeAsc(Long stageId);

	/**
	 * 경기 목록 (API-04). [from, to) 구간의 경기를 팀·스테이지와 함께 읽는다.
	 * stageId, teamId가 null이면 그 조건은 걸지 않는다. teamId는 양 팀 중 어느 쪽이든 일치하면 된다.
	 */
	@Query("""
			select m from LckMatch m
			join fetch m.team1
			join fetch m.team2
			join fetch m.stage st
			where m.matchTime >= :from and m.matchTime < :to
			and (:stageId is null or st.id = :stageId)
			and (:teamId is null or m.team1.id = :teamId or m.team2.id = :teamId)
			order by m.matchTime asc
			""")
	List<LckMatch> findForList(
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("stageId") Long stageId,
			@Param("teamId") Long teamId);

	/** 월별 캘린더 (API-05). [from, to) 구간 경기의 시작 일시만 읽는다. */
	@Query("""
			select m.matchTime from LckMatch m
			where m.matchTime >= :from and m.matchTime < :to
			order by m.matchTime asc
			""")
	List<LocalDateTime> findMatchTimes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
