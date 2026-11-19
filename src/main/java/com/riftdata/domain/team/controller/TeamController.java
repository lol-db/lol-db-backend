package com.riftdata.domain.team.controller;

import com.riftdata.domain.team.dto.StandingResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse;
import com.riftdata.domain.team.dto.TeamResponse;
import com.riftdata.domain.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "팀", description = "팀 목록, 순위표(SCR-005), 팀 상세(SCR-006)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamController {

	private final TeamService teamService;

	@Operation(summary = "팀 목록", description = "팀코드 오름차순")
	@GetMapping("/teams")
	public List<TeamResponse> getTeams() {
		return teamService.getTeams();
	}

	@Operation(summary = "정규 시즌 순위표",
			description = "끝난 정규 시즌 경기로 계산한다. 경기 승수 → 세트 득실 → 세트 승수 순 (승자승 미반영)")
	@GetMapping("/standings")
	public List<StandingResponse> getStandings() {
		return teamService.getStandings();
	}

	@Operation(summary = "팀 상세", description = "순위·성적, 로스터, 팀 지표, 최근 5경기. 없는 팀이면 404 TEAM_001")
	@GetMapping("/teams/{teamId}")
	public TeamDetailResponse getTeam(@PathVariable Long teamId) {
		return teamService.getTeam(teamId);
	}
}
