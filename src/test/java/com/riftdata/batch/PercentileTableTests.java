package com.riftdata.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.riftdata.domain.player.service.MetricPercentile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PercentileTableTests {

	@Test
	@DisplayName("API의 MetricPercentile과 같은 값: 백분위 = 100 − 상위 백분율 (동점·역방향 포함)")
	void matchesApiPercentile() {
		Random random = new Random(7);
		List<Double> values = new ArrayList<>();
		for (int i = 0; i < 200; i++) values.add((double) random.nextInt(40));
		values.add(null);
		List<Double> population = values.stream().filter(v -> v != null).toList();
		for (int direction : new int[]{1, -1}) {
			PercentileTable table = new PercentileTable(values, direction);
			for (Double value : population) {
				Double top = MetricPercentile.topPercent(value, population, direction);
				assertThat(table.percentile(value)).isCloseTo(100 - top, within(1e-9));
			}
		}
	}

	@Test
	@DisplayName("값이 1개 이하이거나 없는 값이면 null, 전원 동점이면 50")
	void edgeCases() {
		assertThat(new PercentileTable(List.of(1.0), 1).percentile(1.0)).isNull();
		assertThat(new PercentileTable(List.of(1.0, 2.0), 1).percentile(3.0)).isNull();
		assertThat(new PercentileTable(List.of(1.0, 2.0), 1).percentile(null)).isNull();
		assertThat(new PercentileTable(List.of(5.0, 5.0, 5.0), 1).percentile(5.0)).isEqualTo(50.0);
		assertThat(new PercentileTable(List.of(1.0, 2.0, 3.0), -1).percentile(1.0)).isEqualTo(100.0);
	}
}
