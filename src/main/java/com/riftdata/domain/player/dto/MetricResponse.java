package com.riftdata.domain.player.dto;

import com.riftdata.domain.player.service.PlayerArea;
import com.riftdata.domain.player.service.PlayerMetric;

/**
 * 지표 사전 한 줄 (API-21). GET /api/v1/metrics
 *
 * @param code      지표 코드 (다른 API의 keyMetrics[].code, metrics[].code와 같은 값)
 * @param abbr      화면 표시용 약어 (예: GD@15)
 * @param name      지표 이름
 * @param area      영역 COMBAT, DAMAGE, LANE, GROWTH, VISION, SURVIVAL
 * @param direction 1이면 높을수록 좋음, -1이면 낮을수록 좋음
 * @param formula   계산 방법
 * @param unit      표시 단위. 비율은 "%"(값은 0~1), 단위가 없으면 null
 */
public record MetricResponse(String code, String abbr, String name, PlayerArea area, int direction,
		String formula, String unit) {

	public static MetricResponse from(PlayerMetric metric) {
		return new MetricResponse(metric.name(), metric.abbr(), metric.label(), metric.area(),
				metric.direction(), metric.formula(), metric.unit());
	}
}
