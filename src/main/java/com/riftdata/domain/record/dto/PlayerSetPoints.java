package com.riftdata.domain.record.dto;

import com.riftdata.global.common.Position;
import com.riftdata.global.common.Rating;

/**
 * 한 경기에서 선수 한 명의 세트 하나 기록 (경기 상세의 시리즈 선수 평점용).
 * totalPoints는 DB의 0~100 총 점수이고, 계산 전이면 null이다.
 */
public record PlayerSetPoints(
		Integer setNumber,
		Long teamId,
		Long playerId,
		String nickname,
		Position position,
		Integer totalPoints) {

	public Double rating() {
		return Rating.fromPoints(totalPoints);
	}
}
