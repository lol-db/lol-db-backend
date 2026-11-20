package com.riftdata.domain.record.repository;

import com.riftdata.domain.record.entity.Ban;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BanRepository extends JpaRepository<Ban, Long> {

	List<Ban> findByTeamSetIdOrderByBanOrderAsc(Long teamSetId);

	/** 세트 하나의 양 팀 밴 */
	List<Ban> findByTeamSetMatchSetId(Long setId);

	/** 세트 상세 (API-07). 세트 하나의 양 팀 밴을 챔피언과 함께 밴 순서대로 읽는다. */
	@Query("""
			select b from Ban b
			join fetch b.champion
			where b.teamSet.matchSet.id = :setId
			order by b.banOrder
			""")
	List<Ban> findWithChampionByMatchSetId(@Param("setId") Long setId);
}
