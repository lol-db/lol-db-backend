package com.riftdata.domain.player.dto;

import java.time.LocalDate;

/** API-13의 기본정보, 필터, 요약 통계와 시즌 등급을 담는 응답입니다. */
public record PlayerDetailResponse(
		Long playerId, String name, String position, PlayerProfile.TeamInfo team,
		Filter filter, Summary summary, String grade, boolean gradeAvailable) {

	/** 날짜는 필터를 통과한 출전 기록의 첫날과 마지막 날입니다. 기록이 없으면 null입니다. */
	public record Filter(String patch, Long championId, LocalDate fromDate, LocalDate toDate) {
	}

	/** 비율은 0~1이며, 계산할 자료가 없는 비율·평점·분당 지표는 null입니다. */
	public record Summary(
			int sets, int wins, Double winRate,
			long kills, long deaths, long assists, Double kda,
			Double killParticipation, long teamKills,
			Double csPerMin, Double positionAvgCsPerMin,
			Double avgRating, Double bestRating, int pogCount) {
	}
}
