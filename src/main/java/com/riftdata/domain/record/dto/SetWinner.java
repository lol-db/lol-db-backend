package com.riftdata.domain.record.dto;

/**
 * 세트 하나의 승리 팀 (team_set.victory = true인 행).
 * 경기 여러 개의 세트 결과를 한 번에 읽어 경기별 세트 스코어를 계산할 때 쓴다.
 */
public record SetWinner(Long matchId, Integer setNumber, Long winnerTeamId) {
}
