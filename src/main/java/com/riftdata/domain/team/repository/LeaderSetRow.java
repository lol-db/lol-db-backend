package com.riftdata.domain.team.repository;

import com.riftdata.global.common.Position;
import java.time.LocalTime;

/**
 * 지표 리더 계산용 선수 세트 기록 한 줄 (API-09).
 * 15분 골드 차는 같은 세트·같은 포지션의 상대 팀 선수와 비교하므로 setId, teamId, position을 함께 읽는다.
 */
public record LeaderSetRow(
		Long playerId,
		String nickname,
		String teamCode,
		Long setId,
		Long teamId,
		Position position,
		Integer kills,
		Integer deaths,
		Integer assists,
		Integer damageToChampions,
		Integer visionPoints,
		Integer goldAt15,
		Integer totalPoints,
		LocalTime playTime) {
}
