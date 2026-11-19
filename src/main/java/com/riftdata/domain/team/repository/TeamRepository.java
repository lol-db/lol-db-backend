package com.riftdata.domain.team.repository;

import com.riftdata.domain.team.entity.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

	Optional<Team> findByCode(String code);

	List<Team> findAllByOrderByCodeAsc();

	/** 모든 경기의 팀별 세트 승수. 순위표와 최근 경기를 이 결과 하나로 계산한다. */
	@Query("""
			select new com.riftdata.domain.team.repository.MatchTeamResult(
			    m.id, m.matchTime, m.sets, s.name, s.regular, t.id, t.code,
			    sum(case when ts.victory = true then 1L else 0L end), count(ts))
			from TeamSet ts
			    join ts.matchSet ms
			    join ms.match m
			    join m.stage s
			    join ts.team t
			group by m.id, m.matchTime, m.sets, s.name, s.regular, t.id, t.code
			""")
	List<MatchTeamResult> findMatchTeamResults();

	/** 한 팀의 세트 기록 (진영, 승패, 첫 오브젝트, 경기 시간). */
	@Query("""
			select new com.riftdata.domain.team.repository.TeamSetSummary(
			    ms.id, ts.side, ts.victory, ts.firstDragon, ts.firstTower, ts.firstBaron, ms.playTime)
			from TeamSet ts
			    join ts.matchSet ms
			where ts.team.id = :teamId
			""")
	List<TeamSetSummary> findTeamSetSummaries(@Param("teamId") Long teamId);

	/** 한 팀이 뛴 세트마다 양 팀의 15분 골드 합계 (상대 팀 행 포함). */
	@Query("""
			select new com.riftdata.domain.team.repository.SetGoldAt15(
			    ms.id, ts.team.id, sum(ps.goldAt15), count(ps.goldAt15))
			from PlayerSet ps
			    join ps.teamSet ts
			    join ts.matchSet ms
			where ms.id in (select ts2.matchSet.id from TeamSet ts2 where ts2.team.id = :teamId)
			group by ms.id, ts.team.id
			""")
	List<SetGoldAt15> findGoldAt15InTeamSets(@Param("teamId") Long teamId);

	/** 팀의 현재 로스터와 시즌 지표 (시즌 지표가 아직 없는 선수도 포함). */
	@Query("""
			select new com.riftdata.domain.team.repository.RosterRow(p, season)
			from Player p
			    left join PlayerSeason season on season.player = p
			where p.team.id = :teamId
			""")
	List<RosterRow> findRoster(@Param("teamId") Long teamId);
}
