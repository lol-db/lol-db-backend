package com.riftdata.domain.team.repository;

import java.time.LocalDateTime;

/**
 * 경기 하나에서 한 팀이 거둔 세트 성적 (경기당 2행).
 * team_set을 경기·팀 단위로 묶은 결과라 아직 세트 기록이 없는 경기는 나오지 않는다.
 */
public record MatchTeamResult(
		Long matchId,
		LocalDateTime matchTime,
		Integer bestOf,
		String stageName,
		Boolean regular,
		Long teamId,
		String teamCode,
		Long setWins,
		Long setsPlayed) {

	/** 이 팀이 이 경기를 이겼는지. Bo3는 2세트, Bo5는 3세트를 먼저 따면 승리다. */
	public boolean won() {
		return setWins >= bestOf / 2 + 1;
	}
}
