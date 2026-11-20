package com.riftdata.domain.team.dto;

/**
 * 지표 리더 한 명 (API-09). GET /api/v1/standings/leaders
 *
 * @param metric KDA, DPM, GD15, VSPM, AVG_RATING
 * @param name   선수 닉네임
 * @param value  평균 평점은 소수 1자리, KDA·DPM·GD15·VSPM은 소수 2자리
 */
public record LeaderResponse(String metric, String label, Long playerId, String name, String teamCode, Number value) {
}
