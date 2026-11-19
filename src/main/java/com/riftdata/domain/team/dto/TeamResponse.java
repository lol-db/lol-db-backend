package com.riftdata.domain.team.dto;

import com.riftdata.domain.team.entity.Team;

/** 팀 목록 한 줄 (API-10). GET /api/v1/teams. 팀명 컬럼이 DB에 없어 name은 null이다. */
public record TeamResponse(Long teamId, String code, String name) {

	public static TeamResponse from(Team team) {
		return new TeamResponse(team.getId(), team.getCode(), null);
	}
}
