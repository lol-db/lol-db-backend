package com.riftdata.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RatingTests {

	@ParameterizedTest
	@CsvSource({
			"0, 2.0",
			"50, 6.0",
			"88, 9.0",   // 6.0 + 38 / 12.5 = 9.04
			"95, 9.6",   // 9.6
			"57, 6.6",   // 6.56 → 6.6
			"100, 10.0"
	})
	void convertsPointsToRating(int points, double expected) {
		assertThat(Rating.fromPoints(points)).isEqualTo(expected);
	}

	@Test
	void returnsNullBeforeCalculation() {
		assertThat(Rating.fromPoints(null)).isNull();
	}

	@Test
	void addsKoreanOffset() {
		assertThat(KstTime.toOffset(LocalDateTime.of(2026, 4, 26, 17, 0)))
				.hasToString("2026-04-26T17:00+09:00");
		assertThat(KstTime.toOffset(null)).isNull();
	}

	@Test
	void averagesRatingsSkippingNull() {
		assertThat(Rating.average(Arrays.asList(8.4, null, 6.1, 8.9))).isEqualTo(7.8);
		assertThat(Rating.average(Arrays.asList(null, null))).isNull();
		assertThat(Rating.average(List.of())).isNull();
	}
}
