package com.riftdata.domain.match.dto.response;

import com.riftdata.domain.match.dto.MatchStatus;
import com.riftdata.global.common.Position;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 경기(시리즈) 상세 (API-06, SCR-003): 헤더, 세트 탭, 시리즈 선수 평점, 세트별 POG.
 * 예정 경기면 sets, playerRatings, pogs는 []이다.
 */
public record MatchDetailResponse(
		Long matchId,
		StageResponse stage,
		OffsetDateTime startAt,
		MatchStatus status,
		Integer bestOf,
		Team teamA,
		Team teamB,
		List<SetSummary> sets,
		List<TeamPlayerRatings> playerRatings,
		List<SetPogResponse> pogs) {

	/**
	 * 헤더의 팀 한쪽. score는 이긴 세트 수(예정이면 null),
	 * rank·wins·losses는 경기 시점이 아닌 현재 순위다 (순위표에 없으면 null).
	 */
	public record Team(Long teamId, String code, Integer score, Integer rank, Integer wins, Integer losses) {
	}

	/** 세트 탭 하나. durationSec는 경기 시간(초)이다. */
	public record SetSummary(Integer setNo, Long winnerTeamId, Integer durationSec) {
	}

	/** 한 팀 선수들의 시리즈 평점. players는 포지션 순(TOP → SUPPORT)이다. */
	public record TeamPlayerRatings(Long teamId, List<PlayerRating> players) {
	}

	/**
	 * 선수 한 명의 시리즈 평점. setRatings 길이는 치른 세트 수이고,
	 * 그 세트에 안 나왔거나 평점 계산 전이면 null이다. avgRating은 값이 있는 세트의 평균이다.
	 */
	public record PlayerRating(
			Long playerId, String name, Position position, List<Double> setRatings, Double avgRating) {
	}

	/** 세트별 POG */
	public record SetPogResponse(
			Integer setNo, Long playerId, String name, Long teamId, ChampionResponse champion, Double rating) {
	}
}
