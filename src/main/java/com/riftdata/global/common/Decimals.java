package com.riftdata.global.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 응답 숫자의 소수 자릿수 공통 규칙. 모든 도메인이 같은 값을 같은 자릿수로 보내도록 여기서만 반올림한다.
 * 반올림은 HALF_UP이고, 계산할 수 없는 값(null)은 그대로 null이다.
 *
 * <pre>
 * 비율 (승률, 킬 관여율, 점유율 등 0~1)                     소수 3자리   rate
 * KDA, 분당 지표 (DPM, CSM, GPM, VSPM 등), 골드·CS 차 평균   소수 2자리   stat
 * 점수 (0~100)                                            소수 1자리   score
 * 평점 (0~10)                                             소수 1자리   Rating
 * </pre>
 */
public final class Decimals {

	private static final int RATE_SCALE = 3;
	private static final int STAT_SCALE = 2;
	private static final int SCORE_SCALE = 1;

	private Decimals() {
	}

	/** 0~1 비율, 소수 3자리 */
	public static Double rate(Double value) {
		return scale(value, RATE_SCALE);
	}

	/** 분자 ÷ 분모를 소수 3자리 비율로. 분모가 0이면 null */
	public static Double rate(long numerator, long denominator) {
		if (denominator == 0) {
			return null;
		}
		return BigDecimal.valueOf(numerator)
				.divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP)
				.doubleValue();
	}

	/** KDA, 분당 지표, 골드·CS 차 평균. 소수 2자리 */
	public static Double stat(Double value) {
		return scale(value, STAT_SCALE);
	}

	/** (K + A) ÷ max(D, 1), 소수 2자리 */
	public static Double kda(long kills, long deaths, long assists) {
		return BigDecimal.valueOf(kills + assists)
				.divide(BigDecimal.valueOf(Math.max(deaths, 1)), STAT_SCALE, RoundingMode.HALF_UP)
				.doubleValue();
	}

	/** 0~100 점수, 소수 1자리 */
	public static Double score(Double value) {
		return scale(value, SCORE_SCALE);
	}

	private static Double scale(Double value, int scale) {
		if (value == null) {
			return null;
		}
		return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
	}
}
