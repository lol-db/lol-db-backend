package com.riftdata.domain.player.repository;

import com.riftdata.domain.player.entity.PlayerSeason;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSeasonRepository extends JpaRepository<PlayerSeason, Long> {

	Optional<PlayerSeason> findByPlayerId(Long playerId);
}
