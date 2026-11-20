package com.riftdata.global.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * 세트 평점. DB에는 선수세트지표 총 점수(player_set.total_points, 0~100)만 저장하고,
 * 화면에 보이는 0~10 평점은 응답을 만들 때 이 식으로 바꾼다 (API 명세서 1.5 계산 규칙).
 *
 * <pre>
 * 평점 = 6.0 + (총 점수 − 50) ÷ 12.5, 0~10으로 자르고 소수 1자리 반올림
 * 0점 → 2.0, 50점 → 6.0, 100점 → 10.0
 * </pre>
 */
public final class Rating {

	private static final double BASE = 6.0;
	private static final double MID_POINTS = 50.0;
	private static final double POINTS_PER_RATING = 12.5;

	private Rating() {
	}

	/** 총 점수를 평점으로. 아직 계산 전(NULL)이면 null */
	public static Double fromPoints(Integer totalPoints) {
		if (totalPoints == null) {
			return null;
		}
		double rating = BASE + (totalPoints - MID_POINTS) / POINTS_PER_RATING;
		double clamped = Math.clamp(rating, 0.0, 10.0);
		return BigDecimal.valueOf(clamped).setScale(1, RoundingMode.HALF_UP).doubleValue();
	}

	/** 평점들의 평균, 소수 1자리 반올림. null은 빼고, 값이 하나도 없으면 null */
	public static Double average(Collection<Double> ratings) {
		OptionalDouble avg = ratings.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
		if (avg.isEmpty()) {
			return null;
		}
		return BigDecimal.valueOf(avg.getAsDouble()).setScale(1, RoundingMode.HALF_UP).doubleValue();
	}
}
