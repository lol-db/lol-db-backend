package com.riftdata.domain.team.service;

import com.riftdata.domain.team.dto.StandingResponse;
import com.riftdata.domain.team.dto.TeamStanding;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 경기 도메인이 쓰는 TeamStandingReader 구현. 순위표(TeamService.getStandings)와 같은 계산을 쓴다.
 * 경기 쪽 테스트가 TeamStandingReader를 목으로 바꿔도 TeamService는 그대로 남도록 클래스를 나눴다.
 */
@Service
@RequiredArgsConstructor
public class TeamStandingService implements TeamStandingReader {

	private final TeamService teamService;

	@Override
	public Map<Long, TeamStanding> findByTeamIds(Collection<Long> teamIds) {
		Set<Long> wanted = Set.copyOf(teamIds);
		return teamService.getStandings().stream()
				.filter(row -> wanted.contains(row.teamId()))
				.collect(Collectors.toMap(StandingResponse::teamId, TeamStandingService::toStanding));
	}

	private static TeamStanding toStanding(StandingResponse row) {
		return new TeamStanding(row.teamId(), row.rank(), row.wins(), row.losses());
	}
}
