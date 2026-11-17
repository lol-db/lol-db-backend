package com.riftdata.domain.record.dto;

import com.riftdata.global.common.Position;
import com.riftdata.global.common.Rating;
import java.time.LocalDateTime;

/**
 * 기간 안의 평점이 있는 선수 세트 기록 하나 (홈의 최근 평점 상위 선수용).
 * teamCode·position은 선수의 현재 소속·포지션이다.
 */
public record RatedSet(
		Long playerId,
		String nickname,
		String teamCode,
		Position position,
		Long championId,
		String championName,
		Integer totalPoints,
		LocalDateTime matchTime) {

	public Double rating() {
		return Rating.fromPoints(totalPoints);
	}
}
