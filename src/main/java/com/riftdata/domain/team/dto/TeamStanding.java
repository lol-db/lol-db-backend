package com.riftdata.domain.team.dto;

/**
 * 팀 하나의 현재 순위 (API-08 순위표와 같은 계산).
 * 경기 상세의 팀 헤더, 홈의 순위 미니표에서 쓴다. setDiff는 세트 득실(이긴 세트 − 진 세트)이다.
 */
public record TeamStanding(Long teamId, Integer rank, Integer wins, Integer losses, Integer setDiff) {
}
