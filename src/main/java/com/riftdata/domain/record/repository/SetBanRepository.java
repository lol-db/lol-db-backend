package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.entity.SetBan;
import com.riftdata.domain.record.entity.SetBanId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetBanRepository extends JpaRepository<SetBan, SetBanId> {

	List<SetBan> findByIdMatchIdAndIdSetNo(Integer matchId, Integer setNo);
}
