package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerCompareResponse;
import com.riftdata.domain.player.service.PlayerCompareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "선수")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerCompareController {

	private final PlayerCompareService playerCompareService;

	@Operation(summary = "선수 비교 (API-18)",
			description = "두 선수의 시즌 점수·영역 점수, KDA와 핵심 지표 10개(더 좋은 쪽 better), 같은 세트 맞대결 기록. "
					+ "배열은 [p1, p2] 순서. 포지션이 다르면 등급은 null. p1·p2가 없거나 같으면 400 COMMON_001, 없는 선수면 404 PLAYER_001")
	@GetMapping("/compare")
	public PlayerCompareResponse compare(
			@Parameter(description = "선수 1 ID", required = true) @RequestParam(required = false) Long p1,
			@Parameter(description = "선수 2 ID", required = true) @RequestParam(required = false) Long p2) {
		return playerCompareService.compare(p1, p2);
	}
}
