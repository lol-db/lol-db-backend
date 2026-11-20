package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerDetailResponse;
import com.riftdata.domain.player.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프론트의 선수 상세 조회 요청을 받는 창구입니다. */
@Tag(name = "선수", description = "선수 랭킹(API-12, SCR-007), 선수 상세·종합·경기 기록(API-13~15, SCR-008·013)")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {
	private final PlayerService playerService;

	/** 선수 상세 모든 탭에서 사용하는 기본정보와 요약 카드입니다. */
	@Operation(summary = "선수 상세 헤더·요약 카드 (API-13)",
			description = "모든 선수 탭 위에 붙는 기본정보와 요약 카드. 필터가 있거나 10세트 미만이면 grade=null, gradeAvailable=false. 없는 선수면 404 PLAYER_001")
	@GetMapping("/{playerId}")
	public PlayerDetailResponse getPlayer(
			@PathVariable Long playerId,
			@Parameter(description = "패치 필터 (예: 26.8). 비우면 전체") @RequestParam(required = false) String patch,
			@Parameter(description = "챔피언 ID 필터. 비우면 전체") @RequestParam(required = false) Long championId) {
		return playerService.getDetail(playerId, patch, championId);
	}
}
