package com.riftdata.domain.team.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 시즌 정보와 필터 목록 (API-01). GET /api/v1/meta
 *
 * @param stages        명세의 rounds 대신 DB의 stage 목록 (진행 순서). 경기 목록의 stageId 필터에 쓴다
 * @param lastUpdatedAt 선수 시즌 지표를 마지막으로 계산한 시각. 수집 이력 테이블이 없어 대신 쓴다. 없으면 null
 */
public record MetaResponse(
		Season season,
		List<String> patches,
		List<Stage> stages,
		int minSetsForGrade,
		OffsetDateTime lastUpdatedAt,
		String dataSource) {

	/**
	 * @param startDate    첫 stage 시작일. stage가 없으면 null
	 * @param endDate      마지막 stage 종료일. stage가 없으면 null
	 * @param currentStage 오늘이 기간에 들어가는 stage. 없으면 null (명세의 currentRound 대신)
	 */
	public record Season(
			String name,
			LocalDate startDate,
			LocalDate endDate,
			int teamCount,
			int playoffTeamCount,
			Stage currentStage) {
	}

	public record Stage(Long stageId, String name, boolean regular) {
	}
}
