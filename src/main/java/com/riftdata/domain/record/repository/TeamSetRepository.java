package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.entity.TeamSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamSetRepository extends JpaRepository<TeamSet, Long> {

	/** 세트의 양 팀 기록 (2행) */
	List<TeamSet> findByMatchSetId(Long setId);

	Optional<TeamSet> findByMatchSetIdAndTeamId(Long setId, Long teamId);

	List<TeamSet> findByTeamId(Long teamId);
}
