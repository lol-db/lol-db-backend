package com.riftdata.domain.champion.controller;

import com.riftdata.domain.champion.dto.ChampionPlayerResponse;
import com.riftdata.domain.champion.dto.ChampionStatsResponse;
import com.riftdata.domain.champion.service.ChampionService;
import com.riftdata.global.common.Position;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "챔피언", description = "챔피언 통계(API-19), 챔피언을 잘 쓰는 선수(API-20) — SCR-016")
@RestController
@RequestMapping("/api/v1/champions")
@RequiredArgsConstructor
public class ChampionController {

	private final ChampionService championService;

	@Operation(summary = "챔피언 통계 (API-19)",
			description = "챔피언별 주 포지션·픽·밴·픽밴률·승률·KDA·평균 평점·최다 사용 선수. 픽밴률 = (픽 + 밴) ÷ totalSets, 내림차순. "
					+ "position은 픽에만 적용(밴에는 포지션이 없음). 잘못된 position·patch는 400 COMMON_001")
	@GetMapping
	public ChampionStatsResponse getChampions(
			@Parameter(description = "패치 필터 (예: 26.8). 비우면 전체") @RequestParam(required = false) String patch,
			@Parameter(description = "포지션 필터. 그 포지션에서 픽된 챔피언만, 픽 수도 그 포지션만") @RequestParam(required = false) Position position) {
		return championService.getChampions(patch, position);
	}

	@Operation(summary = "챔피언을 잘 쓰는 선수 (API-20)",
			description = "그 챔피언으로 3세트 이상 출전한 선수. 평균 평점 내림차순 → 세트 수 → 닉네임. 없는 챔피언이면 404 CHAMPION_001")
	@GetMapping("/{championId}/players")
	public List<ChampionPlayerResponse> getChampionPlayers(@PathVariable Long championId) {
		return championService.getChampionPlayers(championId);
	}
}
