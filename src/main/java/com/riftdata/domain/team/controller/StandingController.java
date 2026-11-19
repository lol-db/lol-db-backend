package com.riftdata.domain.team.controller;

import com.riftdata.domain.team.dto.LeaderResponse;
import com.riftdata.domain.team.dto.StandingsResponse;
import com.riftdata.domain.team.service.LeaderService;
import com.riftdata.domain.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "순위", description = "순위표(API-08), 지표 리더(API-09) — SCR-005")
@RestController
@RequestMapping("/api/v1/standings")
@RequiredArgsConstructor
public class StandingController {

	private final TeamService teamService;
	private final LeaderService leaderService;

	@Operation(summary = "정규 시즌 순위표 (API-08)",
			description = "끝난 정규 시즌 경기로 계산한다. 승 → 세트 득실 → 승자승, 승자승까지 같으면 같은 순위")
	@GetMapping
	public StandingsResponse getStandings() {
		return teamService.getStandingsTable();
	}

	@Operation(summary = "지표 리더 (API-09)",
			description = "전체 포지션, 10세트 이상. KDA, DPM, GD15, VSPM, 평균 평점 1위")
	@GetMapping("/leaders")
	public List<LeaderResponse> getLeaders() {
		return leaderService.getLeaders();
	}
}
