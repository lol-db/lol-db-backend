package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.entity.PlayerSet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSetRepository extends JpaRepository<PlayerSet, Long> {

	/** 선수별 세트 기록 (ix_ps_player) */
	List<PlayerSet> findByPlayerId(Long playerId);

	/** 챔피언 통계 (ix_ps_champion) */
	List<PlayerSet> findByChampionId(Long championId);

	/** 팀세트기록 하나의 선수 5명 */
	List<PlayerSet> findByTeamSetId(Long teamSetId);

	/** 세트 하나의 선수 10명 */
	List<PlayerSet> findByTeamSetMatchSetId(Long setId);
}
