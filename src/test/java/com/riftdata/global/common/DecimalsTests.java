package com.riftdata.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecimalsTests {

	@Test
	@DisplayName("비율은 소수 3자리, HALF_UP")
	void rate() {
		assertThat(Decimals.rate(2, 3)).isEqualTo(0.667);
		assertThat(Decimals.rate(13, 15)).isEqualTo(0.867);
		assertThat(Decimals.rate(1, 8)).isEqualTo(0.125);
		assertThat(Decimals.rate(0.0005)).isEqualTo(0.001);
		assertThat(Decimals.rate(1, 0)).isNull();
		assertThat(Decimals.rate(null)).isNull();
	}

	@Test
	@DisplayName("KDA는 (K + A) ÷ max(D, 1), 소수 2자리")
	void kda() {
		assertThat(Decimals.kda(6, 3, 7)).isEqualTo(4.33);
		assertThat(Decimals.kda(84, 40, 108)).isEqualTo(4.8);
		// 2.005처럼 double로 정확히 표현되지 않는 경계값도 HALF_UP으로 올린다 (401 ÷ 200)
		assertThat(Decimals.kda(200, 200, 201)).isEqualTo(2.01);
		assertThat(Decimals.kda(3, 0, 2)).isEqualTo(5.0);
	}

	@Test
	@DisplayName("분당 지표·골드 차 평균은 소수 2자리, 점수는 소수 1자리")
	void statAndScore() {
		assertThat(Decimals.stat(166.66666)).isEqualTo(166.67);
		assertThat(Decimals.stat(-12.345)).isEqualTo(-12.35);
		assertThat(Decimals.stat(null)).isNull();
		assertThat(Decimals.score(76.25)).isEqualTo(76.3);
		assertThat(Decimals.score(null)).isNull();
	}
}
