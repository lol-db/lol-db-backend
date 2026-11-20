package com.riftdata.domain.player.dto;

import java.util.List;

/**
 * 선수 챔피언 탭 (API-17, SCR-012). GET /api/v1/players/{playerId}/champions
 *
 * @param championPool 챔피언 폭
 * @param champions    챔피언별 성적. sort 기준 내림차순, 같으면 세트 수 → 챔피언 ID 순
 */
public record PlayerChampionsResponse(ChampionPool championPool, List<ChampionStat> champions) {

	/**
	 * @param count            사용한 챔피언 수
	 * @param topShare         가장 많이 쓴 챔피언의 세트 비중 0~1. 세트가 없으면 null
	 * @param positionAvgCount 같은 포지션 비교 집단(시즌 10세트 이상)의 평균 챔피언 수. 같은 필터 적용
	 */
	public record ChampionPool(int count, Double topShare, Double positionAvgCount) {
	}

	/**
	 * @param winRate    세트 승률 0~1
	 * @param kda        (K + A) ÷ max(D, 1), 세트 합계
	 * @param dpm        분당 피해량, 세트 평균
	 * @param goldDiff15 15분 골드 차, 상대 같은 포지션과 비교한 세트 평균. 값이 있는 세트가 없으면 null
	 * @param avgRating  세트 평점 평균. 계산된 평점이 없으면 null
	 * @param lowSample  5세트 미만이면 true (화면의 "참고용")
	 * @param setRatings 세트 평점 분포 그래프용, 시간 순. 평점 계산 전인 세트는 뺀다
	 */
	public record ChampionStat(Long championId, String name, int sets, int wins, int losses, Double winRate,
			int kills, int deaths, int assists, Double kda, Double dpm, Double goldDiff15, Double avgRating,
			boolean lowSample, List<Double> setRatings) {
	}
}
