package com.riftdata.domain.match.dto.response;

import com.riftdata.domain.match.dto.MatchStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 경기 목록의 경기 카드 하나 (API-04).
 * bestOf는 lck_match.sets(bo3 = 3, bo5 = 5)이고, sets는 실제로 치른 세트의 승리 팀이다.
 * 예정 경기면 sets는 [], pog는 null이다.
 */
public record MatchSummaryResponse(
		Long matchId,
		LocalDate date,
		OffsetDateTime startAt,
		StageResponse stage,
		MatchStatus status,
		Integer bestOf,
		TeamScoreResponse teamA,
		TeamScoreResponse teamB,
		List<SetResult> sets,
		Pog pog) {

	/** 세트 하나의 승리 팀 */
	public record SetResult(Integer setNo, Long winnerTeamId) {
	}

	/** 시리즈 POG (세트 POG를 가장 많이 받은 선수) */
	public record Pog(Long playerId, String name) {
	}
}
