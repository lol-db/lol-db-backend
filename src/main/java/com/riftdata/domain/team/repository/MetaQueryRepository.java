package com.riftdata.domain.team.repository;

import com.riftdata.domain.match.entity.Stage;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.projection.ChampionHit;
import com.riftdata.domain.team.repository.projection.PlayerHit;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 메타(API-01)·검색(API-02)·지표 리더(API-09)용 조회 쿼리. 모두 읽기 전용이다.
 * 검색은 대소문자를 무시하고, 앞부분이 맞는 결과를 먼저, 그다음 중간에 들어 있는 결과를 이름순으로 준다.
 */
public interface MetaQueryRepository extends Repository<Team, Long> {

	@Query("select s from Stage s order by s.stageOrder")
	List<Stage> findStages();

	@Query("select distinct ms.version from MatchSet ms where ms.version is not null")
	List<String> findPatches();

	@Query("select max(ps.calculatedAt) from PlayerSeason ps")
	LocalDateTime findLastCalculatedAt();

	@Query("select count(t) from Team t")
	long countTeams();

	@Query("""
			select new com.riftdata.domain.team.repository.projection.PlayerHit(p.id, p.nickname, p.position, t.id, t.code)
			from Player p left join p.team t
			where lower(p.nickname) like :contains escape '!'
			order by case when lower(p.nickname) like :prefix escape '!' then 0 else 1 end, p.nickname
			""")
	List<PlayerHit> searchPlayers(@Param("prefix") String prefix, @Param("contains") String contains, Pageable limit);

	@Query("""
			select t from Team t
			where lower(t.code) like :contains escape '!'
			order by case when lower(t.code) like :prefix escape '!' then 0 else 1 end, t.code
			""")
	List<Team> searchTeams(@Param("prefix") String prefix, @Param("contains") String contains, Pageable limit);

	@Query("""
			select new com.riftdata.domain.team.repository.projection.ChampionHit(c.id, c.name) from Champion c
			where lower(c.name) like :contains escape '!'
			order by case when lower(c.name) like :prefix escape '!' then 0 else 1 end, c.name
			""")
	List<ChampionHit> searchChampions(@Param("prefix") String prefix, @Param("contains") String contains, Pageable limit);

	/** 모든 선수 세트 기록 (지표 리더용). 선수의 현재 소속 팀코드를 함께 읽는다. */
	@Query("""
			select new com.riftdata.domain.team.repository.LeaderSetRow(
			    p.id, p.nickname, pt.code, ms.id, ts.team.id, ps.position,
			    ps.kills, ps.deaths, ps.assists, ps.damageToChampions, ps.visionPoints, ps.goldAt15, ps.totalPoints,
			    ms.playTime)
			from PlayerSet ps
			    join ps.player p
			    left join p.team pt
			    join ps.teamSet ts
			    join ts.matchSet ms
			""")
	List<LeaderSetRow> findLeaderSetRows();
}
