package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerGamesResponse;
import com.riftdata.domain.player.dto.PlayerPageResponse;
import com.riftdata.domain.player.service.PlayerGamesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 선수의 경기 기록 탭을 조회하는 API입니다. */
@Tag(name = "선수")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerGamesController {
	private final PlayerGamesService playerGamesService;

	@Operation(summary = "선수 경기 기록 탭 (API-15)",
			description = "경기 단위로 묶고 페이지도 경기 단위. 최근 경기 먼저, 경기 안은 세트 번호 순. 조건에 맞는 세트가 없는 경기는 빠짐. 경기 점수는 필터 전 전체 세트 기준. 없는 선수면 404 PLAYER_001")
	@GetMapping("/{playerId}/games")
	public PlayerPageResponse<PlayerGamesResponse> getGames(
			@PathVariable Long playerId,
			@Parameter(description = "세트 승패 WIN / LOSS. 비우면 전체")
			@RequestParam(required = false) String result,
			@Parameter(description = "상대 팀 ID")
			@RequestParam(required = false) Long opponentTeamId,
			@Parameter(description = "패치 필터 (예: 26.8). 비우면 전체") @RequestParam(required = false) String patch,
			@Parameter(description = "챔피언 ID 필터. 비우면 전체") @RequestParam(required = false) Long championId,
			@Parameter(description = "페이지 번호 (0부터, 경기 단위)")
			@RequestParam(defaultValue = "0") int page,
			@Parameter(description = "페이지 크기 (1~100, 경기 단위)")
			@RequestParam(defaultValue = "3") int size) {
		return playerGamesService.getGames(playerId, result, opponentTeamId, patch, championId, page, size);
	}
}
