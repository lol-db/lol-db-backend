package com.riftdata.domain.match.repository;

import com.riftdata.domain.match.entity.LckMatch;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LckMatchRepository extends JpaRepository<LckMatch, Long> {

	/** 날짜별 일정·결과 (SCR-002, ix_match_time) */
	List<LckMatch> findByMatchTimeBetweenOrderByMatchTimeAsc(LocalDateTime from, LocalDateTime to);

	List<LckMatch> findByStageIdOrderByMatchTimeAsc(Long stageId);
}
