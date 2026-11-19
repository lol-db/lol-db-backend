package com.riftdata.domain.team.repository;

/**
 * 한 세트에서 한 팀 선수들의 15분 골드 합계.
 * playersWithData가 5보다 작으면 일부 선수의 15분 값이 없다는 뜻이다.
 */
public record SetGoldAt15(Long setId, Long teamId, Long goldAt15, Long playersWithData) {
}
