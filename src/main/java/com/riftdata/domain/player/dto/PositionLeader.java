package com.riftdata.domain.player.dto;

import com.riftdata.global.common.Position;

/** 포지션 하나의 종합 점수 1위 선수 (홈의 포지션별 리더). 등급이 있는 선수(10세트 이상)만 대상이다. */
public record PositionLeader(
		Position position, Long playerId, String name, String teamCode, Double overallScore, String grade) {
}
