package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerOverviewResponse;
import com.riftdata.domain.player.service.PlayerOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 선수 종합 탭 API-14입니다. */
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerOverviewController {
	private final PlayerOverviewService playerOverviewService;

	@GetMapping("/{playerId}/overview")
	public PlayerOverviewResponse getOverview(@PathVariable Long playerId,
			@RequestParam(required = false) String patch,
			@RequestParam(required = false) Long championId) {
		return playerOverviewService.getOverview(playerId, patch, championId);
	}
}
