package com.riftdata.domain.match.repository;

import com.riftdata.domain.match.entity.LckMatch;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LckMatchRepository extends JpaRepository<LckMatch, Integer> {

	/** 날짜별 일정·결과 (SCR-002) */
	List<LckMatch> findByMatchAtBetweenOrderByMatchAtAsc(LocalDateTime from, LocalDateTime to);

	List<LckMatch> findByStageStageCodeOrderByMatchAtAsc(String stageCode);
}
