package com.riftdata.domain.team.repository;

/** 로스터 선수가 뛴 세트 하나의 기록 (평균 평점·KDA·대표 챔피언 집계용). totalPoints는 계산 전이면 null. */
public record RosterSetRow(
		Long playerId,
		Long championId,
		String championName,
		Integer kills,
		Integer deaths,
		Integer assists,
		Integer totalPoints) {
}
