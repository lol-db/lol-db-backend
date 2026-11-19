package com.riftdata.domain.record.dto;

import com.riftdata.global.common.Rating;
import java.time.LocalDateTime;

/** 기간 안의 세트 POG 하나 (홈의 최근 POG용). teamCode는 그 세트에서 뛴 팀이다. */
public record RecentPog(
		LocalDateTime matchTime,
		Long matchId,
		Integer setNumber,
		Long playerId,
		String nickname,
		String teamCode,
		Long championId,
		String championName,
		Integer totalPoints) {

	public Double rating() {
		return Rating.fromPoints(totalPoints);
	}
}
