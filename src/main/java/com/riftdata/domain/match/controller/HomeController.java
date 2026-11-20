package com.riftdata.domain.match.controller;

import com.riftdata.domain.match.dto.response.HomeResponse;
import com.riftdata.domain.match.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈", description = "홈 화면(API-03, SCR-001)")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

	private final HomeService homeService;

	/** 홈 화면 (API-03, SCR-001). date가 없으면 오늘, 시연 때는 2026-04-26처럼 날짜를 고정한다. */
	@Operation(summary = "홈 화면 (API-03)",
			description = "오늘 경기, 최근 7일 평점 상위 5명(3세트 이상), 순위 상위 6팀, 포지션별 종합 점수 1위, 최근 3일 POG 3명을 한 번에")
	@GetMapping
	public HomeResponse getHome(
			@Parameter(description = "기준일 (yyyy-MM-dd). 비우면 오늘, 시연 때는 2026-04-26처럼 고정")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return homeService.getHome(date);
	}
}
