package com.riftdata.domain.match.controller;

import com.riftdata.domain.match.dto.response.HomeResponse;
import com.riftdata.domain.match.service.HomeService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

	private final HomeService homeService;

	/** 홈 화면 (API-03, SCR-001). date가 없으면 오늘, 시연 때는 2026-04-26처럼 날짜를 고정한다. */
	@GetMapping
	public HomeResponse getHome(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return homeService.getHome(date);
	}
}
