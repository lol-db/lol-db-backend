package com.riftdata.domain.team.dto;

import java.util.List;

/**
 * 순위표 한 줄 (API-08 rows[]).
 *
 * @param rank    순위. 승수·세트 득실·승자승까지 같으면 같은 순위
 * @param matches 끝난 정규 시즌 경기 수
 * @param setDiff 세트 득실 (setWins - setLosses)
 * @param winRate 경기 승률 0~1, 소수 3자리. 경기가 없으면 0
 * @param recent5 최근 5경기 결과 "W"/"L", 최근 경기부터
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
		double winRate,
		List<String> recent5) {
}
