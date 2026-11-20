package com.riftdata.domain.team.controller;

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

@Tag(name = "팀", description = "팀 목록(API-10), 팀 상세(API-11, SCR-006)")
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

	private final TeamService teamService;

	@Operation(summary = "팀 목록 (API-10)", description = "팀코드 오름차순. 팀명 컬럼이 없어 teamId, code만 보냄")
	@GetMapping
	public List<TeamResponse> getTeams() {
		return teamService.getTeams();
	}

	@Operation(summary = "팀 상세 (API-11)",
			description = "순위·성적, 로스터, 팀 지표와 리그 내 순위, 최근 5경기. 없는 팀이면 404 TEAM_001")
	@GetMapping("/{teamId}")
	public TeamDetailResponse getTeam(@PathVariable Long teamId) {
		return teamService.getTeam(teamId);
	}
}
