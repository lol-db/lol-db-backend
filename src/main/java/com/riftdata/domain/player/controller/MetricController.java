package com.riftdata.domain.player.controller;

import com.riftdata.domain.player.dto.MetricResponse;
import com.riftdata.domain.player.service.PlayerMetric;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지표", description = "지표 사전(API-21)")
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

	private static final List<MetricResponse> METRICS =
			Arrays.stream(PlayerMetric.values()).map(MetricResponse::from).toList();

	@Operation(summary = "지표 사전 (API-21)",
			description = "선수 지표 20개의 약어·이름·영역·방향·공식·단위. 영역 순(전투 → 딜량 → 라인전 → 성장 → 시야 → 생존). 화면 툴팁과 등급 기준 안내에 사용")
	@GetMapping
	public List<MetricResponse> getMetrics() {
		return METRICS;
	}
}
