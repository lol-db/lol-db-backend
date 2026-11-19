package com.riftdata.domain.record.dto;

import com.riftdata.global.common.Rating;

/**
 * 세트 하나의 POG (player_set.is_pog = true인 행).
 * totalPoints는 DB의 0~100 총 점수이고, 화면 평점은 {@link #rating()}으로 구한다.
 */
public record SetPog(
		Long matchId,
		Integer setNumber,
		Long teamId,
		Long playerId,
		String nickname,
		Long championId,
		String championName,
		Integer totalPoints) {

	public Double rating() {
		return Rating.fromPoints(totalPoints);
	}
}
