package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerPageResponse;
import com.riftdata.domain.player.dto.PlayerRankingResponse;
import com.riftdata.domain.player.service.PlayerRankingService;
import com.riftdata.global.common.Position;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 선수 목록과 랭킹을 조회하는 API-12입니다. */
@Tag(name = "선수")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/players")
public class PlayerRankingController {
	private final PlayerRankingService playerRankingService;

	@Operation(summary = "선수 랭킹 (API-12)",
			description = "내림차순, 공동 순위(1, 1, 3). 10세트 미만 선수는 기본 제외, 포함하면 맨 뒤에 rank·점수·등급 null로 표시. 잘못된 sort·page·size는 400 COMMON_001")
	@GetMapping
	public PlayerPageResponse<PlayerRankingResponse> getRanking(
			@Parameter(description = "포지션. 비우면 전체")
			@RequestParam(required = false) Position position,
			@Parameter(description = "overallScore(기본), avgRating, sets 또는 영역 코드 COMBAT·DAMAGE·LANE·GROWTH·VISION·SURVIVAL")
			@RequestParam(defaultValue = "overallScore") String sort,
			@Parameter(description = "10세트 미만 선수 포함 여부")
			@RequestParam(defaultValue = "false") boolean includeLowSample,
			@Parameter(description = "닉네임 부분 검색 (대소문자 무시)")
			@RequestParam(required = false) String q,
			@Parameter(description = "페이지 번호 (0부터)")
			@RequestParam(defaultValue = "0") int page,
			@Parameter(description = "페이지 크기 (1~100)")
			@RequestParam(defaultValue = "20") int size) {
		return playerRankingService.getRanking(position, sort, includeLowSample, q, page, size);
	}
}
