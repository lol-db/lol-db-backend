package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.entity.PlayerSetStat;
import com.riftdata.domain.record.entity.PlayerSetStatId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSetStatRepository extends JpaRepository<PlayerSetStat, PlayerSetStatId> {

	/** 선수별 세트 기록 (ix_pss_player) */
	List<PlayerSetStat> findByPlayerId(Integer playerId);

	/** 챔피언 통계 (ix_pss_champion) */
	List<PlayerSetStat> findByChampionId(Integer championId);

	List<PlayerSetStat> findByIdMatchIdAndIdSetNo(Integer matchId, Integer setNo);
}
