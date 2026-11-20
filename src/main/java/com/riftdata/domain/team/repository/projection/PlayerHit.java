package com.riftdata.domain.team.repository.projection;

import com.riftdata.global.common.Position;

/** 검색에 걸린 선수 한 명. */
public record PlayerHit(Long playerId, String nickname, Position position, Long teamId, String teamCode) {
}
