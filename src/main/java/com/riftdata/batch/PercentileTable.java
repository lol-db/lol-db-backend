package com.riftdata.batch;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * 한 지표의 값 분포에서 백분위를 구하는 표. 정렬해 두고 이분 탐색하므로 값이 많아도 빠르다.
 * 계산식은 API의 MetricPercentile과 같다 (동점은 평균 순위, 소수 8자리에서 같은 값으로 봄).
 *
 * <pre>
 * 백분위 = 100 × (더 나쁜 값 수 + (같은 값 수 − 1) ÷ 2) ÷ (전체 수 − 1)
 * </pre>
 */
final class PercentileTable {

	private static final double SCALE = 1e8;

	private final double[] sorted;
	private final int direction;

	/** @param direction 1이면 높을수록 좋고 -1이면 낮을수록 좋다 */
	PercentileTable(Collection<Double> values, int direction) {
		this.sorted = values.stream().filter(Objects::nonNull).mapToDouble(PercentileTable::normalize).sorted().toArray();
		this.direction = direction;
	}

	/** 0~100, 클수록 좋음. 값이 없거나 비교할 값이 1개 이하이거나 값이 표에 없으면 null */
	Double percentile(Double value) {
		if (value == null || sorted.length < 2) return null;
		double target = normalize(value);
		int lower = firstIndexAtLeast(target);
		int upper = firstIndexAbove(target);
		int same = upper - lower;
		if (same == 0) return null;
		int worse = direction >= 0 ? lower : sorted.length - upper;
		return 100.0 * (worse + (same - 1) / 2.0) / (sorted.length - 1);
	}

	private int firstIndexAtLeast(double target) {
		int index = Arrays.binarySearch(sorted, target);
		if (index < 0) return -index - 1;
		while (index > 0 && sorted[index - 1] == target) index--;
		return index;
	}

	private int firstIndexAbove(double target) {
		int index = Arrays.binarySearch(sorted, target);
		if (index < 0) return -index - 1;
		while (index < sorted.length && sorted[index] == target) index++;
		return index;
	}

	private static double normalize(double value) {
		return Math.round(value * SCALE) / SCALE;
	}
}
