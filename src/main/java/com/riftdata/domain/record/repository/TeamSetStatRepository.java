package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.entity.TeamSetStat;
import com.riftdata.domain.record.entity.TeamSetStatId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamSetStatRepository extends JpaRepository<TeamSetStat, TeamSetStatId> {

	List<TeamSetStat> findByIdMatchId(Integer matchId);

	List<TeamSetStat> findByIdTeamId(Integer teamId);
}
