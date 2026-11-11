package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.entity.Ban;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BanRepository extends JpaRepository<Ban, Long> {

	List<Ban> findByTeamSetIdOrderByBanOrderAsc(Long teamSetId);

	/** 세트 하나의 양 팀 밴 */
	List<Ban> findByTeamSetMatchSetId(Long setId);
}
