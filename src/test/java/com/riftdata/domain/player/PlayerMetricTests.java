package com.riftdata.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.TeamTotals;
import com.riftdata.domain.player.service.PlayerArea;
import com.riftdata.domain.player.service.PlayerMetric;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 지표 계산 단위 테스트. DB 없이 세트 기록을 직접 만들어 계산한다. */
class PlayerMetricTests {

	/** 세트 2개: 30분·20분. 팀세트 1, 2 */
	private static final List<OverviewSet> ROWS = List.of(
			set(1L, 1800, 4, 2, 6, 300, 18_000, 12_000, 30, 15, 6, 3,
					true, false, false, 2, 3, 1, 600.0),
			set(2L, 1200, 2, 0, 2, 200, 12_000, 8_000, 20, 5, 4, 1,
					false, false, true, null, null, null, null));

	private static final Map<Long, TeamTotals> TEAM = Map.of(
			1L, new TeamTotals(20, 60_000, 50_000),
			2L, new TeamTotals(10, 40_000, 30_000));

	@Test
	@DisplayName("전투: KDA는 합계, KP는 팀 전체 킬 대비, FB%는 세트 비율, K+A@15는 값 있는 세트만 평균")
	void combat() {
		assertThat(PlayerMetric.KDA.compute(ROWS, TEAM)).isEqualTo((6 + 8) / 2.0);
		assertThat(PlayerMetric.KP.compute(ROWS, TEAM)).isCloseTo(14 / 30.0, within(1e-9));
		assertThat(PlayerMetric.FB_PCT.compute(ROWS, TEAM)).isEqualTo(0.5);
		assertThat(PlayerMetric.KA15.compute(ROWS, TEAM)).isEqualTo(5.0);
	}

	@Test
	@DisplayName("딜량·성장: 비중은 팀 합계 대비, 골드당 피해량은 획득 골드 기준")
	void damageAndGrowth() {
		// DPM: (18000/30 + 12000/20) / 2 = (600 + 600) / 2
		assertThat(PlayerMetric.DPM.compute(ROWS, TEAM)).isEqualTo(600.0);
		assertThat(PlayerMetric.DMG_PCT.compute(ROWS, TEAM)).isEqualTo(30_000 / 100_000.0);
		assertThat(PlayerMetric.DMG_PER_GOLD.compute(ROWS, TEAM)).isEqualTo(30_000 / 20_000.0);
		assertThat(PlayerMetric.GOLD_PCT.compute(ROWS, TEAM)).isEqualTo(20_000 / 80_000.0);
		// CSM: (300/30 + 200/20) / 2
		assertThat(PlayerMetric.CSM.compute(ROWS, TEAM)).isEqualTo(10.0);
	}

	@Test
	@DisplayName("시야·생존: 분당 와드, 세트당 제어 와드, 선취점 데스 비율, 15분 데스")
	void visionAndSurvival() {
		// WPM: (15/30 + 5/20) / 2, WCPM: (6/30 + 4/20) / 2
		assertThat(PlayerMetric.WPM.compute(ROWS, TEAM)).isCloseTo(0.375, within(1e-9));
		assertThat(PlayerMetric.WCPM.compute(ROWS, TEAM)).isCloseTo(0.2, within(1e-9));
		assertThat(PlayerMetric.CW.compute(ROWS, TEAM)).isEqualTo(2.0);
		// D10: (2/3 + 0/2) / 2
		assertThat(PlayerMetric.D10.compute(ROWS, TEAM)).isCloseTo(1 / 3.0, within(1e-9));
		assertThat(PlayerMetric.FD_PCT.compute(ROWS, TEAM)).isEqualTo(0.5);
		assertThat(PlayerMetric.D15.compute(ROWS, TEAM)).isEqualTo(1.0);
	}

	@Test
	@DisplayName("15분 차이는 값이 있는 세트만, 세트가 없으면 null, 0초 세트는 분당 지표에서 제외")
	void missingValues() {
		assertThat(PlayerMetric.GD15.compute(ROWS, TEAM)).isEqualTo(600.0);
		assertThat(PlayerMetric.CSD15.compute(ROWS, TEAM)).isNull();
		assertThat(PlayerMetric.KDA.compute(List.of(), TEAM)).isNull();
		List<OverviewSet> zeroSecond = List.of(set(1L, 0, 1, 1, 1, 100, 1000, 1000, 10, 1, 1, 1,
				false, false, false, null, null, null, null));
		assertThat(PlayerMetric.DPM.compute(zeroSecond, TEAM)).isNull();
		assertThat(PlayerMetric.KP.compute(zeroSecond, Map.of())).isNull();
	}

	@Test
	@DisplayName("자릿수: 비율 3자리, 나머지 2자리. 영역별 지표 목록")
	void roundingAndAreas() {
		assertThat(PlayerMetric.KP.round(14 / 30.0)).isEqualTo(0.467);
		assertThat(PlayerMetric.KDA.round(14 / 3.0)).isEqualTo(4.67);
		assertThat(PlayerMetric.of(PlayerArea.VISION))
				.containsExactly(PlayerMetric.VSPM, PlayerMetric.WPM, PlayerMetric.WCPM, PlayerMetric.CW);
		assertThat(PlayerMetric.KEY_METRICS).hasSize(10);
	}

	private static OverviewSet set(Long teamSetId, int durationSec, int kills, int deaths, int assists, int cs,
			int damage, int earnedGold, int vision, int wardsPlaced, int wardsRemoved, int controlWards,
			boolean fbKill, boolean fbAssist, boolean fbDeath, Integer kills15, Integer assists15,
			Integer deaths15, Double goldDiff15) {
		return new OverviewSet(1L, teamSetId, teamSetId, LocalDateTime.of(2026, 4, 1, 17, 0), 1, durationSec,
				1L, "Champ", 2L, "OPP", true, kills, deaths, assists, cs, damage, earnedGold, vision,
				goldDiff15, null, null, 70, false, fbKill, fbAssist, fbDeath, kills15, assists15, deaths15,
				wardsPlaced, wardsRemoved, controlWards, 9L, "Enemy");
	}
}
