package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerPageResponse;
import com.riftdata.domain.player.dto.PlayerRankingResponse;
import com.riftdata.domain.player.service.PlayerRankingService;
import com.riftdata.global.common.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 선수 목록과 랭킹을 조회하는 API-12입니다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/players")
public class PlayerRankingController {
	private final PlayerRankingService playerRankingService;

	@GetMapping
	public PlayerPageResponse<PlayerRankingResponse> getRanking(
			@RequestParam(required = false) Position position,
			@RequestParam(defaultValue = "overallScore") String sort,
			@RequestParam(defaultValue = "false") boolean includeLowSample,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return playerRankingService.getRanking(position, sort, includeLowSample, q, page, size);
	}
}
