package com.riftdata.domain.player.dto;

import com.riftdata.domain.player.service.PlayerArea;
import com.riftdata.global.common.Position;
import java.util.List;

/** 선수 랭킹 한 행의 기본정보와 시즌 점수를 담는 응답입니다. */
public record PlayerRankingResponse(
		Integer rank, Long playerId, String name, Position position, PlayerProfile.TeamInfo team,
		int sets, Double avgRating, Double overallScore, String grade,
		List<AreaScore> areas, boolean lowSample) {

	/** 표본이나 계산된 점수가 부족하면 점수와 등급은 null입니다. */
	public record AreaScore(PlayerArea area, Double score, String grade) {
	}
}
