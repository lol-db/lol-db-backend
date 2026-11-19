package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerOverviewResponse;
import com.riftdata.domain.player.dto.PlayerOverviewResponse.*;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.repository.PlayerOverviewRepository;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.TeamTotals;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.global.common.Rating;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-14의 저장된 시즌 점수와 원천 기록 기반 지표를 구성합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerOverviewService {
	private static final int MIN_SETS = 10;
	private static final Comparator<OverviewSet> CHRONOLOGICAL = Comparator
			.comparing(OverviewSet::playedAt).thenComparing(OverviewSet::matchId).thenComparingInt(OverviewSet::setNo);
	private final PlayerRepository playerRepository;
	private final PlayerSeasonRepository playerSeasonRepository;
	private final PlayerOverviewRepository overviewRepository;

	public PlayerOverviewResponse getOverview(Long playerId, String patch, Long championId) {
		String normalizedPatch = PlayerQuerySupport.normalizePatch(patch);
		PlayerQuerySupport.validateChampionId(championId);
		Player player = PlayerQuerySupport.requirePlayer(playerRepository, playerId);
		boolean filtered = normalizedPatch != null || championId != null;
		List<OverviewSet> allRecords = overviewRepository.findPositionSets(
				player.getPosition(), normalizedPatch, championId);
		List<OverviewSet> own = allRecords.stream().filter(row -> row.playerId().equals(playerId)).toList();
		Map<Long, Long> seasonCounts = overviewRepository.findPositionSetCounts(player.getPosition());
		Map<Long, TeamTotals> teamTotals = overviewRepository.findTeamTotals(
				allRecords.stream().map(OverviewSet::teamSetId).distinct().toList());
		Map<Long, List<OverviewSet>> byPlayer = allRecords.stream()
				.collect(Collectors.groupingBy(OverviewSet::playerId));
		Map<Long, Map<String, Double>> comparable = new LinkedHashMap<>();
		byPlayer.forEach((id, records) -> {
			if (seasonCounts.getOrDefault(id, 0L) >= MIN_SETS) {
				comparable.put(id, metrics(records, teamTotals));
			}
		});
		boolean eligible = seasonCounts.getOrDefault(playerId, 0L) >= MIN_SETS;
		PlayerSeason season = playerSeasonRepository.findByPlayerId(playerId).orElse(null);
		int playerCount = (int) seasonCounts.values().stream().filter(count -> count >= MIN_SETS).count();
		Map<String, Double> ownMetrics = metrics(own, teamTotals);
		List<KeyMetric> keyMetrics = Arrays.stream(Metric.values()).map(metric -> {
			Double value = ownMetrics.get(metric.name());
			List<Double> population = comparable.values().stream()
					.map(values -> values.get(metric.name())).filter(Objects::nonNull).toList();
			Double top = !filtered && eligible ? topPercent(value, population, metric.direction) : null;
			int places = metric == Metric.KP || metric == Metric.DMG_PCT ? 4 : 2;
			return new KeyMetric(metric.name(), metric.label, PlayerQuerySupport.round(value, places),
					PlayerQuerySupport.round(mean(population), places),
					PlayerQuerySupport.round(top, 2), gradeFromTop(top), metric.direction);
		}).toList();

		List<RecentSet> recentSets = own.stream().sorted(CHRONOLOGICAL.reversed()).limit(5)
				.map(row -> new RecentSet(row.matchId(), row.setNo(), row.playedAt().toLocalDate(),
						new TeamInfo(row.opponentId(), row.opponentCode()),
						new ChampionInfo(row.championId(), row.championName()), row.kills(), row.deaths(),
						row.assists(), row.win(), Rating.fromPoints(row.totalPoints()))).toList();
		List<RatingPoint> trend = own.stream().sorted(CHRONOLOGICAL)
				.map(row -> new RatingPoint(row.matchId(), row.setNo(), row.playedAt().toLocalDate(),
						Rating.fromPoints(row.totalPoints()), row.pog())).toList();
		return new PlayerOverviewResponse(new CompareGroup(player.getPosition(), playerCount, MIN_SETS),
				overall(season, eligible && !filtered),
				filtered ? null : areaScores(player, season, eligible),
				mostChampions(own), keyMetrics, recentSets, trend);
	}

	private static Overall overall(PlayerSeason season, boolean eligible) {
		if (!eligible || season == null || season.getSets() < MIN_SETS) return null;
		String grade = PlayerGrade.fromPoints(season.getTotalPoints());
		if (grade == null) return null;
		Integer rank = season.getRankInPosition();
		return new Overall(season.getTotalPoints().doubleValue(), grade,
				100.0 - season.getTotalPoints(), rank != null && rank > 0 ? rank : null);
	}

	private static List<AreaScore> areaScores(Player player, PlayerSeason season, boolean eligible) {
		boolean scored = eligible && season != null && season.getSets() >= MIN_SETS;
		return Arrays.stream(PlayerArea.values()).map(area -> {
			Integer points = scored ? area.points(season) : null;
			return new AreaScore(area.name(), area.label(), points == null ? null : points.doubleValue(),
					PlayerGrade.fromPoints(points), points == null ? null : 100.0 - points,
					area.weight(player.getPosition()));
		}).toList();
	}

	private static List<MostChampion> mostChampions(List<OverviewSet> records) {
		Map<Long, List<OverviewSet>> grouped = records.stream().collect(Collectors.groupingBy(OverviewSet::championId));
		return grouped.entrySet().stream()
				.sorted(Comparator.<Map.Entry<Long, List<OverviewSet>>>comparingInt(entry -> entry.getValue().size())
						.reversed().thenComparing(Map.Entry::getKey))
				.limit(4).map(entry -> {
					List<OverviewSet> rows = entry.getValue();
					long kills = rows.stream().mapToLong(OverviewSet::kills).sum();
					long assists = rows.stream().mapToLong(OverviewSet::assists).sum();
					long deaths = rows.stream().mapToLong(OverviewSet::deaths).sum();
					Double rating = mean(rows.stream().map(row -> Rating.fromPoints(row.totalPoints()))
							.filter(Objects::nonNull).toList());
					return new MostChampion(entry.getKey(), rows.getFirst().championName(), rows.size(),
							PlayerQuerySupport.round(rows.stream().filter(OverviewSet::win).count() / (double) rows.size(), 4),
							rounded((kills + assists) / (double) Math.max(deaths, 1)),
							rounded(perMinute(rows, OverviewSet::damage, 1)),
							PlayerQuerySupport.round(rating, 1));
				}).toList();
	}

	/** 분당 지표는 세트별 값의 평균이며 팀 비율의 분모에는 당시 팀원 전체를 포함합니다. */
	private static Map<String, Double> metrics(List<OverviewSet> rows, Map<Long, TeamTotals> teamTotals) {
		Map<String, Double> values = new LinkedHashMap<>();
		long teamKills = rows.stream().map(OverviewSet::teamSetId).distinct()
				.mapToLong(id -> teamTotals.getOrDefault(id, new TeamTotals(0, 0)).kills()).sum();
		long teamDamage = rows.stream().map(OverviewSet::teamSetId).distinct()
				.mapToLong(id -> teamTotals.getOrDefault(id, new TeamTotals(0, 0)).damage()).sum();
		long damage = rows.stream().mapToLong(OverviewSet::damage).sum();
		long involved = rows.stream().mapToLong(row -> (long) row.kills() + row.assists()).sum();
		values.put("DPM", perMinute(rows, OverviewSet::damage, 1));
		values.put("DMG_PCT", teamDamage == 0 ? null : damage / (double) teamDamage);
		values.put("KP", teamKills == 0 ? null : involved / (double) teamKills);
		values.put("GD15", average(rows, OverviewSet::goldDiff15));
		values.put("CSD15", average(rows, OverviewSet::csDiff15));
		values.put("XPD15", average(rows, OverviewSet::xpDiff15));
		values.put("GPM", perMinute(rows, OverviewSet::earnedGold, 1));
		values.put("CSM", perMinute(rows, OverviewSet::cs, 1));
		values.put("VSPM", perMinute(rows, OverviewSet::vision, 1));
		values.put("D10", perMinute(rows, OverviewSet::deaths, 10));
		return values;
	}

	private static Double perMinute(List<OverviewSet> rows, ToDoubleFunction<OverviewSet> numerator, int factor) {
		return mean(rows.stream().filter(row -> row.durationSec() > 0)
				.map(row -> numerator.applyAsDouble(row) * 60 * factor / row.durationSec()).toList());
	}

	private static Double average(List<OverviewSet> rows, Function<OverviewSet, Double> value) {
		return mean(rows.stream().map(value).filter(Objects::nonNull).toList());
	}

	private static Double mean(List<Double> values) {
		return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
	}

	private static Double rounded(Double value) {
		return PlayerQuerySupport.round(value, 2);
	}

	/** 동점에는 평균 순위를 적용합니다. 비교 가능한 선수가 한 명이면 백분위를 만들지 않습니다. */
	private static Double topPercent(Double value, List<Double> population, int direction) {
		if (value == null || population.size() < 2) return null;
		long worse = 0;
		long same = 0;
		BigDecimal target = BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
		for (Double candidate : population) {
			int comparison = BigDecimal.valueOf(candidate).setScale(8, RoundingMode.HALF_UP).compareTo(target);
			if (comparison == 0) same++;
			else if (comparison * direction < 0) worse++;
		}
		if (same == 0) return null;
		double percentile = 100.0 * (worse + (same - 1) / 2.0) / (population.size() - 1);
		return Math.max(0, Math.min(100, 100 - percentile));
	}

	private static String gradeFromTop(Double top) {
		if (top == null) return null;
		if (top <= 5) return "A+";
		if (top <= 15) return "A";
		if (top <= 25) return "A-";
		if (top <= 40) return "B+";
		if (top <= 55) return "B";
		if (top <= 70) return "B-";
		if (top <= 85) return "C+";
		return "C";
	}

	private enum Metric {
		DPM("분당 피해량", 1), DMG_PCT("팀 내 피해 비중", 1), KP("킬 관여율", 1),
		GD15("15분 골드 차", 1), CSD15("15분 CS 차", 1), XPD15("15분 경험치 차", 1),
		GPM("분당 획득 골드", 1), CSM("분당 CS", 1), VSPM("분당 시야 점수", 1), D10("10분당 데스", -1);
		private final String label;
		private final int direction;
		Metric(String label, int direction) {
			this.label = label;
			this.direction = direction;
		}
	}
}
