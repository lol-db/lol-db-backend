package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerGamesResponse;
import com.riftdata.domain.player.dto.PlayerPageResponse;
import com.riftdata.domain.player.service.PlayerGamesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 선수의 경기 기록 탭을 조회하는 API입니다. */
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerGamesController {
	private final PlayerGamesService playerGamesService;

	@GetMapping("/{playerId}/games")
	public PlayerPageResponse<PlayerGamesResponse> getGames(
			@PathVariable Long playerId,
			@RequestParam(required = false) String result,
			@RequestParam(required = false) Long opponentTeamId,
			@RequestParam(required = false) String patch,
			@RequestParam(required = false) Long championId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "3") int size) {
		return playerGamesService.getGames(playerId, result, opponentTeamId, patch, championId, page, size);
	}
}
