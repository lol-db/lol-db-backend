package com.riftdata.domain.match.repository;

import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.match.entity.MatchSetId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchSetRepository extends JpaRepository<MatchSet, MatchSetId> {

	List<MatchSet> findByIdMatchIdOrderByIdSetNoAsc(Integer matchId);

	Optional<MatchSet> findBySourceGameId(String sourceGameId);
}
