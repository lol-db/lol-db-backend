package com.riftdata.domain.player.repository;

import com.riftdata.domain.player.entity.Player;
import com.riftdata.global.common.Position;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	Optional<Player> findByNickname(String nickname);

	/** 공통 헤더 검색 */
	List<Player> findByNicknameContainingIgnoreCase(String keyword);

	/** 포지션별 랭킹 (ix_player_position) */
	List<Player> findByPosition(Position position);

	List<Player> findByTeamId(Long teamId);
}
