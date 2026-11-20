package com.riftdata.domain.match.controller;

import com.riftdata.domain.match.dto.MatchStatus;
import com.riftdata.domain.match.dto.response.MatchCalendarResponse;
import com.riftdata.domain.match.dto.response.MatchDetailResponse;
import com.riftdata.domain.match.dto.response.MatchSummaryResponse;
import com.riftdata.domain.match.dto.response.SetDetailResponse;
import com.riftdata.domain.match.service.MatchService;
import com.riftdata.domain.match.service.SetDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "경기", description = "경기 목록·캘린더(API-04, 05, SCR-002), 경기 상세(API-06, SCR-003), 세트 상세(API-07, SCR-004)")
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

	private final MatchService matchService;
	private final SetDetailService setDetailService;

	/** 경기 목록 (API-04, SCR-002). 명세서의 round 대신 stageId로 거른다. */
	@Operation(summary = "경기 목록 (API-04)",
			description = "최근 날짜 먼저, 같은 날은 시작 시각 순. 명세서의 round 대신 stageId로 거른다. from이 to보다 늦으면 400 COMMON_001")
	@GetMapping
	public List<MatchSummaryResponse> getMatches(
			@Parameter(description = "시작일 (yyyy-MM-dd). 비우면 to − 6일")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "종료일 (yyyy-MM-dd). 비우면 오늘")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@Parameter(description = "스테이지 ID (GET /meta의 stages). 비우면 전체")
			@RequestParam(required = false) Long stageId,
			@Parameter(description = "이 팀이 나온 경기만")
			@RequestParam(required = false) Long teamId,
			@Parameter(description = "FINISHED(종료) / SCHEDULED(예정). 비우면 전체")
			@RequestParam(required = false) MatchStatus status) {
		return matchService.getMatches(from, to, stageId, teamId, status);
	}

	/** 경기(시리즈) 상세 (API-06, SCR-003) */
	@Operation(summary = "경기(시리즈) 상세 (API-06)",
			description = "헤더, 세트 목록, 시리즈 선수 평점, 세트별 POG. 팀 rank·wins·losses는 현재 순위. 없는 경기면 404 MATCH_002")
	@GetMapping("/{matchId}")
	public MatchDetailResponse getMatch(@PathVariable Long matchId) {
		return matchService.getMatch(matchId);
	}

	/** 세트 상세 (API-07, SCR-004) */
	@Operation(summary = "세트 상세 (API-07)",
			description = "밴픽, 팀 오브젝트, 스코어보드, 골드 차(블루 − 레드). 없는 경기면 404 MATCH_002, 경기는 있는데 세트가 없으면 404 MATCH_003")
	@GetMapping("/{matchId}/sets/{setNo}")
	public SetDetailResponse getSet(@PathVariable Long matchId, @PathVariable Integer setNo) {
		return setDetailService.getSet(matchId, setNo);
	}

	/** 월별 경기 캘린더 (API-05, SCR-002). month는 "2026-04" 형식이다. */
	@Operation(summary = "월별 경기 캘린더 (API-05)", description = "경기가 있는 날과 경기 수. 날짜 바의 점 표시용")
	@GetMapping("/calendar")
	public MatchCalendarResponse getCalendar(
			@Parameter(description = "조회할 달 (yyyy-MM, 예: 2026-04)", required = true)
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return matchService.getCalendar(month);
	}
}
