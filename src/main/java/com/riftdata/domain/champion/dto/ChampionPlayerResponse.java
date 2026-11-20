package com.riftdata.domain.champion.dto;

/**
 * 챔피언을 잘 쓰는 선수 한 명 (API-20). GET /api/v1/champions/{championId}/players
 * 그 챔피언으로 3세트 이상 출전한 선수, 평균 평점 내림차순(없으면 뒤) → 세트 수 → 닉네임 순.
 *
 * @param teamCode  현재 소속 팀
 * @param winRate   이 챔피언으로 출전한 세트의 승률 0~1
 * @param kda       (K + A) ÷ max(D, 1), 세트 합계
 * @param avgRating 평균 평점. 계산된 평점이 없으면 null
 */
public record ChampionPlayerResponse(Long playerId, String name, String teamCode, int sets, Double winRate,
		Double kda, Double avgRating) {
}
