package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerDetailResponse;
import com.riftdata.domain.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프론트의 선수 상세 조회 요청을 받는 창구입니다. */
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {
	private final PlayerService playerService;

	/** 선수 상세 모든 탭에서 사용하는 기본정보와 요약 카드입니다. */
	@GetMapping("/{playerId}")
	public PlayerDetailResponse getPlayer(
			@PathVariable Long playerId,
			@RequestParam(required = false) String patch,
			@RequestParam(required = false) Long championId) {
		return playerService.getDetail(playerId, patch, championId);
	}
}
