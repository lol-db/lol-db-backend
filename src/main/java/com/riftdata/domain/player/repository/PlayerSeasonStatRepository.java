package com.riftdata.domain.player.repository;

import com.riftdata.domain.player.entity.PlayerSeasonStat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSeasonStatRepository extends JpaRepository<PlayerSeasonStat, Integer> {
}
