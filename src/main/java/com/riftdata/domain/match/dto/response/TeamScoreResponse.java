package com.riftdata.domain.match.dto.response;

import com.riftdata.domain.team.entity.Team;

/**
 * 경기 카드의 팀 한쪽. team 테이블에는 팀명이 없어 code만 보낸다.
 * score는 이긴 세트 수이고, 예정 경기면 null이다.
 */
public record TeamScoreResponse(Long teamId, String code, Integer score) {

	public static TeamScoreResponse of(Team team, Integer score) {
		return new TeamScoreResponse(team.getId(), team.getCode(), score);
	}
}
