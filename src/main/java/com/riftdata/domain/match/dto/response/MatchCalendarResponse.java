package com.riftdata.domain.match.dto.response;

import java.time.LocalDate;
import java.util.List;

/** 월별 경기 캘린더 (API-05). 날짜 바에서 경기가 있는 날을 표시할 때 쓴다. month는 "2026-04" 형식이다. */
public record MatchCalendarResponse(String month, List<Day> days) {

	/** 경기가 있는 날 하나. 경기가 없는 날은 목록에 없다. */
	public record Day(LocalDate date, long matchCount) {
	}
}
