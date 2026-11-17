package com.riftdata.domain.match.controller;

import com.riftdata.domain.match.dto.MatchStatus;
import com.riftdata.domain.match.dto.response.MatchCalendarResponse;
import com.riftdata.domain.match.dto.response.MatchSummaryResponse;
import com.riftdata.domain.match.service.MatchService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

	private final MatchService matchService;

	/** 경기 목록 (API-04, SCR-002). 명세서의 round 대신 stageId로 거른다. */
	@GetMapping
	public List<MatchSummaryResponse> getMatches(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long stageId,
			@RequestParam(required = false) Long teamId,
			@RequestParam(required = false) MatchStatus status) {
		return matchService.getMatches(from, to, stageId, teamId, status);
	}

	/** 월별 경기 캘린더 (API-05, SCR-002). month는 "2026-04" 형식이다. */
	@GetMapping("/calendar")
	public MatchCalendarResponse getCalendar(
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return matchService.getCalendar(month);
	}
}
