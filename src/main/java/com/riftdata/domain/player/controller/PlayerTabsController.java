package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.PlayerAreasResponse;
import com.riftdata.domain.player.dto.PlayerChampionsResponse;
import com.riftdata.domain.player.service.PlayerAreasService;
import com.riftdata.domain.player.service.PlayerChampionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 선수 상세의 후순위 탭: 영역 탭(API-16), 챔피언 탭(API-17) */
@Tag(name = "선수")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerTabsController {

	private final PlayerAreasService playerAreasService;
	private final PlayerChampionsService playerChampionsService;

	@Operation(summary = "선수 영역 탭 (API-16)",
			description = "탭 3개를 group 하나로 처리. 영역 카드(시즌 점수·등급, 영역 지표의 값·포지션 평균·상위%), 세트별 추이, 그룹별 추가 정보"
					+ "(combat: 킬 관여 구성·챔피언별 DPM / lane: 라인 상대별 15분 지표 / vision: 와드 요약). "
					+ "필터가 있거나 10세트 미만이면 점수·등급·상위%는 null. group이 잘못되면 400 COMMON_001, 없는 선수면 404 PLAYER_001")
	@GetMapping("/{playerId}/areas")
	public PlayerAreasResponse getAreas(
			@PathVariable Long playerId,
			@Parameter(description = "combat(전투·딜량) / lane(라인전·성장) / vision(생존·시야)", required = true)
			@RequestParam(required = false) String group,
			@Parameter(description = "패치 필터 (예: 26.8). 비우면 전체") @RequestParam(required = false) String patch,
			@Parameter(description = "챔피언 ID 필터. 비우면 전체") @RequestParam(required = false) Long championId) {
		return playerAreasService.getAreas(playerId, group, patch, championId);
	}

	@Operation(summary = "선수 챔피언 탭 (API-17)",
			description = "챔피언 폭(개수·최다 챔피언 비중·포지션 평균 개수)과 챔피언별 성적·세트 평점 분포. 5세트 미만은 lowSample=true. "
					+ "sort가 잘못되면 400 COMMON_001, 없는 선수면 404 PLAYER_001")
	@GetMapping("/{playerId}/champions")
	public PlayerChampionsResponse getChampions(
			@PathVariable Long playerId,
			@Parameter(description = "sets(기본) / winRate / avgRating, 내림차순") @RequestParam(required = false) String sort,
			@Parameter(description = "패치 필터 (예: 26.8). 비우면 전체") @RequestParam(required = false) String patch,
			@Parameter(description = "챔피언 ID 필터. 비우면 전체") @RequestParam(required = false) Long championId) {
		return playerChampionsService.getChampions(playerId, sort, patch, championId);
	}
}
