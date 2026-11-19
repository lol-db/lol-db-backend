package com.riftdata.domain.team.dto;

/** 팀 하나의 현재 순위 (API-08 순위표와 같은 계산). 경기 상세의 팀 헤더에서 쓴다. */
public record TeamStanding(Long teamId, Integer rank, Integer wins, Integer losses) {
}
