package com.riftdata.domain.champion.repository;

import com.riftdata.domain.champion.entity.Champion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChampionRepository extends JpaRepository<Champion, Long> {

	Optional<Champion> findByName(String name);
}
