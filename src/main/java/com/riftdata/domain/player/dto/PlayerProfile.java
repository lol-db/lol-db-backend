package com.riftdata.domain.player.dto;

import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.team.entity.Team;

/** 선수 상세 응답에 사용하는 기본정보입니다. */
public record PlayerProfile(Long playerId, String name, String position, TeamInfo team) {
	public static PlayerProfile from(Player player) {
		Team team = player.getTeam();
		return new PlayerProfile(player.getId(), player.getNickname(),
				player.getPosition().name(), new TeamInfo(team.getId(), team.getCode()));
	}

	/** 팀 이름 컬럼이 없으므로 최신 경기 API와 같이 팀 ID와 코드만 반환합니다. */
	public record TeamInfo(Long teamId, String code) {
	}
}
