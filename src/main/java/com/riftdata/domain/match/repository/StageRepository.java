package com.riftdata.domain.match.repository;

import com.riftdata.domain.match.entity.Stage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageRepository extends JpaRepository<Stage, Long> {

	List<Stage> findAllByOrderByStageOrderAsc();
}
