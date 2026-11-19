package com.riftdata.domain.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.riftdata.domain.player.service.PlayerGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 화면설계서의 등급 컷 경계를 확인합니다. */
class PlayerGradeTests {
	@ParameterizedTest
	@CsvSource({"100,A+", "95,A+", "94,A", "85,A", "84,A-", "75,A-",
			"74,B+", "60,B+", "59,B", "45,B", "44,B-", "30,B-",
			"29,C+", "15,C+", "14,C", "0,C"})
	@DisplayName("상위 5·15·25·40·55·70·85퍼센트 경계에 맞는 등급을 반환합니다.")
	void followsDocumentedGradeBoundaries(Integer points, String expected) {
		assertThat(PlayerGrade.fromPoints(points)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(ints = {-1, 101})
	@DisplayName("점수가 없거나 범위를 벗어나면 등급을 만들지 않습니다.")
	void doesNotInventGrades(Integer points) {
		assertThat(PlayerGrade.fromPoints(points)).isNull();
	}
}
