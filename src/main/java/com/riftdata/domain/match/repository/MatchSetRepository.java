package com.riftdata.domain.match.repository;

import com.riftdata.domain.match.entity.MatchSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchSetRepository extends JpaRepository<MatchSet, Long> {

	List<MatchSet> findByMatchIdOrderBySetNumberAsc(Long matchId);

	Optional<MatchSet> findByMatchIdAndSetNumber(Long matchId, Integer setNumber);

	/** B2 적재 때 이미 들어간 게임인지 확인 */
	Optional<MatchSet> findByGameId(String gameId);
}
