package com.riftdata.domain.match.dto;

/**
 * 경기 상태 (응답용 코드). DB에 상태 컬럼은 없고, 세트 기록이 있으면 FINISHED, 없으면 SCHEDULED로 판단한다.
 * CSV를 적재하는 방식이라 진행 중(LIVE)은 없다.
 */
public enum MatchStatus {
	SCHEDULED, FINISHED;

	public static MatchStatus of(int playedSets) {
		return playedSets > 0 ? FINISHED : SCHEDULED;
	}
}
