package com.riftdata.domain.player.service;

import static com.riftdata.domain.player.service.PlayerArea.COMBAT;
import static com.riftdata.domain.player.service.PlayerArea.DAMAGE;
import static com.riftdata.domain.player.service.PlayerArea.GROWTH;
import static com.riftdata.domain.player.service.PlayerArea.LANE;
import static com.riftdata.domain.player.service.PlayerArea.SURVIVAL;
import static com.riftdata.domain.player.service.PlayerArea.VISION;

import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.TeamTotals;
import com.riftdata.global.common.Decimals;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * 선수 지표 20개의 정의와 계산 (API-21 지표 사전, API-14 핵심 지표, API-16 영역 탭, API-18 선수 비교).
 * 지표정의 테이블이 DB에 없어 코드 상수로 둔다.
 *
 * <ul>
 *   <li>분당·세트당 지표는 세트별 값의 산술평균이고, 경기 시간이 0초인 세트는 분당 지표에서 뺀다.</li>
 *   <li>비율(KP, DMG%, GOLD%)은 누적 분자 ÷ 당시 팀 전체의 누적 분모다. 챔피언 필터를 팀원에게 적용하지 않는다.</li>
 *   <li>15분 차이는 같은 세트 상대 팀의 같은 포지션 선수와 비교하고, 어느 쪽이든 값이 없으면 그 세트를 뺀다.</li>
 *   <li>계산할 세트가 없으면 null이다. 0은 정상적인 값이다.</li>
 * </ul>
 */
public enum PlayerMetric {
	KDA("KDA", "KDA", COMBAT, 1, "(킬 + 어시스트) ÷ max(데스, 1). 세트 합계로 계산", null, false),
	KP("KP", "킬 관여율", COMBAT, 1, "(킬 + 어시스트) ÷ 당시 팀 전체 킬. 세트 합계로 계산", "%", true),
	FB_PCT("FB%", "선취점 관여율", COMBAT, 1, "선취점 킬 또는 어시스트를 기록한 세트 ÷ 출전 세트", "%", true),
	KA15("K+A@15", "15분 킬 관여", COMBAT, 1, "세트별 15분 시점 킬 + 어시스트의 평균", "회", false),

	DPM("DPM", "분당 피해량", DAMAGE, 1, "세트별 챔피언 피해량 ÷ 경기 시간(분)의 평균", null, false),
	DMG_PCT("DMG%", "팀 내 피해 비중", DAMAGE, 1, "챔피언 피해량 ÷ 당시 팀 전체 챔피언 피해량. 세트 합계로 계산", "%", true),
	DMG_PER_GOLD("DMG/G", "골드당 피해량", DAMAGE, 1, "챔피언 피해량 ÷ 획득 골드. 세트 합계로 계산", null, false),

	GD15("GD@15", "15분 골드 차", LANE, 1, "15분 골드 − 상대 같은 포지션의 15분 골드. 세트별 평균", "골드", false),
	CSD15("CSD@15", "15분 CS 차", LANE, 1, "15분 CS − 상대 같은 포지션의 15분 CS. 세트별 평균", "CS", false),
	XPD15("XPD@15", "15분 경험치 차", LANE, 1, "15분 경험치 − 상대 같은 포지션의 15분 경험치. 세트별 평균", "XP", false),

	CSM("CSM", "분당 CS", GROWTH, 1, "세트별 (미니언 + 몬스터 처치) ÷ 경기 시간(분)의 평균", null, false),
	GPM("GPM", "분당 획득 골드", GROWTH, 1, "세트별 획득 골드 ÷ 경기 시간(분)의 평균. 시작·기본 골드를 뺀 획득 골드 기준", null, false),
	GOLD_PCT("GOLD%", "팀 내 골드 비중", GROWTH, 1, "획득 골드 ÷ 당시 팀 전체 획득 골드. 세트 합계로 계산", "%", true),

	VSPM("VSPM", "분당 시야 점수", VISION, 1, "세트별 시야 점수 ÷ 경기 시간(분)의 평균", null, false),
	WPM("WPM", "분당 와드 설치", VISION, 1, "세트별 설치한 와드 수 ÷ 경기 시간(분)의 평균", null, false),
	WCPM("WCPM", "분당 와드 제거", VISION, 1, "세트별 제거한 와드 수 ÷ 경기 시간(분)의 평균", null, false),
	CW("CW", "세트당 제어 와드 구매", VISION, 1, "세트별 제어 와드 구매 수의 평균", "개", false),

	D10("D/10", "10분당 데스", SURVIVAL, -1, "세트별 데스 ÷ 경기 시간(10분)의 평균", null, false),
	FD_PCT("FD%", "선취점 데스 비율", SURVIVAL, -1, "선취점의 희생자가 된 세트 ÷ 출전 세트", "%", true),
	D15("D@15", "15분 데스", SURVIVAL, -1, "세트별 15분 시점 데스의 평균", "회", false);

	/** 종합 탭(API-14)의 핵심 지표 10개, 화면 표시 순서 */
	public static final List<PlayerMetric> KEY_METRICS =
			List.of(DPM, DMG_PCT, KP, GD15, CSD15, XPD15, GPM, CSM, VSPM, D10);

	private final String abbr;
	private final String label;
	private final PlayerArea area;
	private final int direction;
	private final String formula;
	private final String unit;
	private final boolean rate;

	PlayerMetric(String abbr, String label, PlayerArea area, int direction, String formula, String unit, boolean rate) {
		this.abbr = abbr;
		this.label = label;
		this.area = area;
		this.direction = direction;
		this.formula = formula;
		this.unit = unit;
		this.rate = rate;
	}

	public String abbr() {
		return abbr;
	}

	public String label() {
		return label;
	}

	public PlayerArea area() {
		return area;
	}

	/** 1이면 높을수록 좋고, -1이면 낮을수록 좋다. */
	public int direction() {
		return direction;
	}

	public String formula() {
		return formula;
	}

	/** 화면 표시 단위. 비율은 "%"(값은 0~1), 단위가 없는 수치는 null */
	public String unit() {
		return unit;
	}

	/** 영역에 속한 지표, 정의 순서 */
	public static List<PlayerMetric> of(PlayerArea area) {
		return Arrays.stream(values()).filter(metric -> metric.area == area).toList();
	}

	/** 응답 자릿수: 비율은 소수 3자리, 나머지는 2자리 (Decimals 공통 규칙) */
	public Double round(Double value) {
		return rate ? Decimals.rate(value) : Decimals.stat(value);
	}

	/** 여러 지표를 한 번에 계산한다. 반올림 전 값이다. */
	public static Map<PlayerMetric, Double> compute(Collection<PlayerMetric> metrics, List<OverviewSet> rows,
			Map<Long, TeamTotals> teamTotals) {
		Map<PlayerMetric, Double> values = new EnumMap<>(PlayerMetric.class);
		for (PlayerMetric metric : metrics) {
			values.put(metric, metric.compute(rows, teamTotals));
		}
		return values;
	}

	/** 세트 기록으로 이 지표 값을 계산한다. 반올림 전 값이고, 계산할 세트가 없으면 null이다. */
	public Double compute(List<OverviewSet> rows, Map<Long, TeamTotals> teamTotals) {
		if (rows.isEmpty()) {
			return null;
		}
		return switch (this) {
			case KDA -> (sum(rows, OverviewSet::kills) + sum(rows, OverviewSet::assists))
					/ (double) Math.max(sum(rows, OverviewSet::deaths), 1);
			case KP -> ratio(sum(rows, OverviewSet::kills) + sum(rows, OverviewSet::assists),
					teamSum(rows, teamTotals, TeamTotals::kills));
			case FB_PCT -> share(rows, row -> row.firstBloodKill() || row.firstBloodAssist());
			case KA15 -> mean(rows, row -> row.kills15() == null || row.assists15() == null
					? null : (double) row.kills15() + row.assists15());
			case DPM -> perMinute(rows, OverviewSet::damage, 1);
			case DMG_PCT -> ratio(sum(rows, OverviewSet::damage), teamSum(rows, teamTotals, TeamTotals::damage));
			case DMG_PER_GOLD -> ratio(sum(rows, OverviewSet::damage), sum(rows, OverviewSet::earnedGold));
			case GD15 -> mean(rows, OverviewSet::goldDiff15);
			case CSD15 -> mean(rows, OverviewSet::csDiff15);
			case XPD15 -> mean(rows, OverviewSet::xpDiff15);
			case CSM -> perMinute(rows, OverviewSet::cs, 1);
			case GPM -> perMinute(rows, OverviewSet::earnedGold, 1);
			case GOLD_PCT -> ratio(sum(rows, OverviewSet::earnedGold),
					teamSum(rows, teamTotals, TeamTotals::earnedGold));
			case VSPM -> perMinute(rows, OverviewSet::vision, 1);
			case WPM -> perMinute(rows, OverviewSet::wardsPlaced, 1);
			case WCPM -> perMinute(rows, OverviewSet::wardsRemoved, 1);
			case CW -> mean(rows, row -> (double) row.controlWards());
			case D10 -> perMinute(rows, OverviewSet::deaths, 10);
			case FD_PCT -> share(rows, OverviewSet::firstBloodDeath);
			case D15 -> mean(rows, row -> row.deaths15() == null ? null : row.deaths15().doubleValue());
		};
	}

	private static long sum(List<OverviewSet> rows, ToLongFunction<OverviewSet> value) {
		return rows.stream().mapToLong(value).sum();
	}

	/** 당시 팀 전체 합계. 같은 팀세트를 두 번 세지 않는다. */
	private static long teamSum(List<OverviewSet> rows, Map<Long, TeamTotals> teamTotals,
			ToLongFunction<TeamTotals> value) {
		return rows.stream().map(OverviewSet::teamSetId).distinct()
				.mapToLong(id -> value.applyAsLong(teamTotals.getOrDefault(id, TeamTotals.EMPTY))).sum();
	}

	private static Double ratio(long numerator, long denominator) {
		return denominator == 0 ? null : numerator / (double) denominator;
	}

	private static Double share(List<OverviewSet> rows, Predicate<OverviewSet> condition) {
		return rows.stream().filter(condition).count() / (double) rows.size();
	}

	private static Double perMinute(List<OverviewSet> rows, ToDoubleFunction<OverviewSet> numerator, int factor) {
		return mean(rows.stream().filter(row -> row.durationSec() > 0)
				.map(row -> numerator.applyAsDouble(row) * 60 * factor / row.durationSec()).toList());
	}

	private static Double mean(List<OverviewSet> rows, Function<OverviewSet, Double> value) {
		return mean(rows.stream().map(value).filter(Objects::nonNull).toList());
	}

	static Double mean(List<Double> values) {
		return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
	}
}
