package com.riftdata.domain.team.dto;

import com.riftdata.domain.team.entity.Team;

/** 팀 목록 한 줄. GET /api/v1/teams */
public record TeamResponse(Long teamId, String code) {

	public static TeamResponse from(Team team) {
		return new TeamResponse(team.getId(), team.getCode());
	}
}
