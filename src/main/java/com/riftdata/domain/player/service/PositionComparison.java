package com.riftdata.domain.player.service;

import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.repository.PlayerOverviewRepository;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.TeamTotals;
import com.riftdata.global.common.Decimals;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 한 선수와 같은 포지션 비교 집단의 세트 기록 (API-14 종합, API-16 영역, API-17 챔피언 탭).
 *
 * <ul>
 *   <li>비교 집단: 현재 주포지션이 같고 2026 실제 출전이 10세트 이상인 선수</li>
 *   <li>패치·챔피언 필터는 대상 선수와 비교 집단에 똑같이 적용하고, 출전 자격은 시즌 전체로 판단한다.</li>
 *   <li>필터가 있거나 대상 선수가 10세트 미만이면 상위 백분율과 등급을 만들지 않는다.</li>
 * </ul>
 */
public final class PositionComparison {

	public static final int MIN_SETS = 10;

	private final List<OverviewSet> own;
	private final Map<Long, List<OverviewSet>> comparable;
	private final Map<Long, TeamTotals> teamTotals;
	private final boolean eligible;
	private final boolean filtered;
	private final int playerCount;

	private PositionComparison(List<OverviewSet> own, Map<Long, List<OverviewSet>> comparable,
			Map<Long, TeamTotals> teamTotals, boolean eligible, boolean filtered, int playerCount) {
		this.own = own;
		this.comparable = comparable;
		this.teamTotals = teamTotals;
		this.eligible = eligible;
		this.filtered = filtered;
		this.playerCount = playerCount;
	}

	/** patch는 정규화된 값(없으면 null)이다. */
	public static PositionComparison load(PlayerOverviewRepository repository, Player player,
			String patch, Long championId) {
		List<OverviewSet> all = repository.findPositionSets(player.getPosition(), patch, championId);
		Map<Long, Long> seasonCounts = repository.findPositionSetCounts(player.getPosition());
		Map<Long, TeamTotals> teamTotals = repository.findTeamTotals(
				all.stream().map(OverviewSet::teamSetId).distinct().toList());
		Map<Long, List<OverviewSet>> comparable = new LinkedHashMap<>();
		all.stream().collect(Collectors.groupingBy(OverviewSet::playerId)).forEach((id, rows) -> {
			if (seasonCounts.getOrDefault(id, 0L) >= MIN_SETS) {
				comparable.put(id, rows);
			}
		});
		List<OverviewSet> own = all.stream().filter(row -> row.playerId().equals(player.getId())).toList();
		int playerCount = (int) seasonCounts.values().stream().filter(count -> count >= MIN_SETS).count();
		return new PositionComparison(own, comparable, teamTotals,
				seasonCounts.getOrDefault(player.getId(), 0L) >= MIN_SETS,
				patch != null || championId != null, playerCount);
	}

	/** 대상 선수의 필터에 맞는 세트 기록 */
	public List<OverviewSet> own() {
		return own;
	}

	/** 비교 집단 선수별 세트 기록 (필터 적용, 대상 선수도 10세트 이상이면 포함) */
	public Map<Long, List<OverviewSet>> comparable() {
		return comparable;
	}

	public Map<Long, TeamTotals> teamTotals() {
		return teamTotals;
	}

	/** 대상 선수가 시즌 10세트 이상인지 */
	public boolean eligible() {
		return eligible;
	}

	public boolean filtered() {
		return filtered;
	}

	/** 시즌 저장 점수(종합·영역)를 보여 줄 수 있는지: 필터가 없고 10세트 이상 */
	public boolean seasonScoresVisible() {
		return eligible && !filtered;
	}

	/** 비교 집단 선수 수 (필터와 관계없이 시즌 10세트 이상) */
	public int playerCount() {
		return playerCount;
	}

	/** 지표마다 값·포지션 평균·상위 백분율·등급을 계산한다. 값과 평균은 반올림한 값이고, 결과 순서는 metrics 순서와 같다. */
	public Map<PlayerMetric, MetricView> metrics(Collection<PlayerMetric> metrics) {
		Map<PlayerMetric, Double> ownValues = PlayerMetric.compute(metrics, own, teamTotals);
		Map<Long, Map<PlayerMetric, Double>> population = new LinkedHashMap<>();
		comparable.forEach((id, rows) -> population.put(id, PlayerMetric.compute(metrics, rows, teamTotals)));
		Map<PlayerMetric, MetricView> views = new LinkedHashMap<>();
		for (PlayerMetric metric : metrics) {
			Double value = ownValues.get(metric);
			List<Double> values = population.values().stream()
					.map(each -> each.get(metric)).filter(Objects::nonNull).toList();
			Double top = seasonScoresVisible() ? MetricPercentile.topPercent(value, values, metric.direction()) : null;
			views.put(metric, new MetricView(metric, metric.round(value), metric.round(PlayerMetric.mean(values)),
					Decimals.score(top), MetricPercentile.grade(top)));
		}
		return views;
	}

	/**
	 * 지표 하나의 표시 값.
	 *
	 * @param topPercent 상위 백분율 0~100 (소수 1자리). 필터·표본 부족·비교 불가면 null
	 * @param grade      반올림 전 상위 백분율로 판정한 등급. topPercent가 null이면 null
	 */
	public record MetricView(PlayerMetric metric, Double value, Double positionAvg, Double topPercent, String grade) {
	}
}
