package com.riftdata.domain.team.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 순위표 (API-08). GET /api/v1/standings
 *
 * @param asOf             집계에 들어간 마지막 경기 날짜. 끝난 경기가 없으면 null
 * @param playoffTeamCount 플레이오프 진출 팀 수 (설정값, 진출선 표시용)
 */
public record StandingsResponse(LocalDate asOf, int playoffTeamCount, List<StandingResponse> rows) {
}
