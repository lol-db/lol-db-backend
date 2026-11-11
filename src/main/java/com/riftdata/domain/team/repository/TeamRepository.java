package com.riftdata.domain.team.repository;

import com.riftdata.domain.team.entity.Team;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

	Optional<Team> findByCode(String code);
}
