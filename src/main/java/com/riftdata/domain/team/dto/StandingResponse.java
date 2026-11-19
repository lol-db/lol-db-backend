package com.riftdata.domain.team.dto;

import java.util.List;

/**
 * 순위표 한 줄 (SCR-005). GET /api/v1/standings
 *
 * @param rank      순위 (1부터)
 * @param matches   끝난 정규 시즌 경기 수
 * @param setDiff   세트 득실 (setWins - setLosses)
 * @param winRate   경기 승률 0~100 (반올림). 경기가 없으면 0
 * @param recent    최근 5경기 결과 "W"/"L", 오래된 경기 → 최근 경기 순
 */
public record StandingResponse(
		int rank,
		Long teamId,
		String code,
		int matches,
		int wins,
		int losses,
		int setWins,
		int setLosses,
		int setDiff,
		int winRate,
		List<String> recent) {
}
