package com.riftdata.domain.player.repository;

import com.riftdata.domain.player.entity.Player;
import com.riftdata.global.common.Position;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Integer> {

	Optional<Player> findBySourcePlayerId(String sourcePlayerId);

	List<Player> findByPlayerNameContainingIgnoreCase(String keyword);

	List<Player> findByMainPosition(Position position);

	List<Player> findByTeamId(Integer teamId);
}
