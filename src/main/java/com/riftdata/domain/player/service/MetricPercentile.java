package com.riftdata.domain.player.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 지표 값의 포지션 내 상위 백분율과 등급 (API-14 핵심 지표, API-16 영역 탭).
 * 비교 집단은 현재 주포지션이 같고 시즌 출전이 10세트 이상인 선수다.
 */
public final class MetricPercentile {

	private MetricPercentile() {
	}

	/**
	 * 상위 백분율 (0~100, 작을수록 좋음). 동점에는 평균 순위를 적용한다.
	 * 백분위 = 100 × (더 나쁜 선수 수 + (동점 선수 수 − 1) ÷ 2) ÷ (유효 선수 수 − 1), 상위 % = 100 − 백분위.
	 * 값이 없거나, 비교할 선수가 한 명뿐이거나, 대상 값이 집단에 없으면 null이다.
	 *
	 * @param direction 1이면 높을수록 좋고 -1이면 낮을수록 좋다
	 */
	public static Double topPercent(Double value, List<Double> population, int direction) {
		if (value == null || population.size() < 2) return null;
		long worse = 0;
		long same = 0;
		BigDecimal target = BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
		for (Double candidate : population) {
			int comparison = BigDecimal.valueOf(candidate).setScale(8, RoundingMode.HALF_UP).compareTo(target);
			if (comparison == 0) same++;
			else if (comparison * direction < 0) worse++;
		}
		if (same == 0) return null;
		double percentile = 100.0 * (worse + (same - 1) / 2.0) / (population.size() - 1);
		return Math.max(0, Math.min(100, 100 - percentile));
	}

	/** 상위 백분율의 등급 (반올림 전 값으로 판정). 컷은 상위 5/15/25/40/55/70/85%다. */
	public static String grade(Double topPercent) {
		if (topPercent == null) return null;
		if (topPercent <= 5) return "A+";
		if (topPercent <= 15) return "A";
		if (topPercent <= 25) return "A-";
		if (topPercent <= 40) return "B+";
		if (topPercent <= 55) return "B";
		if (topPercent <= 70) return "B-";
		if (topPercent <= 85) return "C+";
		return "C";
	}
}
