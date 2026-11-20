package com.riftdata.domain.champion.dto;

import com.riftdata.global.common.Position;
import java.util.List;

/**
 * 챔피언 통계 (API-19, SCR-016). GET /api/v1/champions
 *
 * @param totalSets 조건(패치)에 맞는 세트 수. 픽밴률의 분모
 * @param champions 픽밴률 내림차순, 같으면 픽 수 → 이름 순
 */
public record ChampionStatsResponse(long totalSets, List<ChampionStat> champions) {

	/**
	 * @param mainPosition 가장 많이 픽된 포지션 (같으면 TOP → SUPPORT 순). 픽이 없으면 null
	 * @param picks        픽 수. position 필터가 있으면 그 포지션의 픽만
	 * @param bans         밴 수. 밴에는 포지션이 없어 position 필터와 관계없이 센다
	 * @param pickBanRate  (픽 + 밴) ÷ totalSets, 0~1
	 * @param winRate      픽한 세트의 승률. 픽이 없으면 null
	 * @param kda          픽한 세트 합계의 (K + A) ÷ max(D, 1). 픽이 없으면 null
	 * @param avgRating    픽한 세트의 평균 평점. 계산된 평점이 없으면 null
	 * @param topPlayer    이 챔피언으로 가장 많이 출전한 선수 (같으면 평균 평점 → 선수 ID 순). 픽이 없으면 null
	 */
	public record ChampionStat(Long championId, String name, Position mainPosition, long picks, long bans,
			Double pickBanRate, Double winRate, Double kda, Double avgRating, TopPlayer topPlayer) {
	}

	public record TopPlayer(Long playerId, String name, int sets) {
	}
}
