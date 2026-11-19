package com.riftdata.domain.team.dto;

import com.riftdata.global.common.Position;
import java.util.List;

/** 통합 검색 결과 (API-02). GET /api/v1/search?q= */
public record SearchResponse(List<PlayerItem> players, List<TeamResponse> teams, List<ChampionItem> champions) {

	/** @param name 선수 닉네임. teamId·teamCode는 소속이 없으면 null */
	public record PlayerItem(Long playerId, String name, Position position, Long teamId, String teamCode) {
	}

	public record ChampionItem(Long championId, String name) {
	}
}
