package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerOverviewResponse;
import com.riftdata.domain.player.service.PlayerOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 선수 종합 탭 API-14입니다. */
@Tag(name = "선수")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerOverviewController {
	private final PlayerOverviewService playerOverviewService;

	@Operation(summary = "선수 종합 탭 (API-14)",
			description = "종합·영역 점수, 모스트 챔피언 4개, 핵심 지표 10개와 포지션 평균, 최근 5세트, 평점 추이. 필터가 있으면 overall·areas와 핵심 지표의 grade·topPercent는 null. 없는 선수면 404 PLAYER_001")
	@GetMapping("/{playerId}/overview")
	public PlayerOverviewResponse getOverview(@PathVariable Long playerId,
			@Parameter(description = "패치 필터 (예: 26.8). 비우면 전체") @RequestParam(required = false) String patch,
			@Parameter(description = "챔피언 ID 필터. 비우면 전체") @RequestParam(required = false) Long championId) {
		return playerOverviewService.getOverview(playerId, patch, championId);
	}
}
