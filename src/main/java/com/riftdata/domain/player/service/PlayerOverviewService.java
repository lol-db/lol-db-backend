package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerOverviewResponse;
import com.riftdata.domain.player.dto.PlayerOverviewResponse.*;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.repository.PlayerOverviewRepository;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Rating;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-14의 저장된 시즌 점수와 원천 기록 기반 지표를 구성합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerOverviewService {
	private static final int MIN_SETS = PositionComparison.MIN_SETS;
	private static final Comparator<OverviewSet> CHRONOLOGICAL = Comparator
			.comparing(OverviewSet::playedAt).thenComparing(OverviewSet::matchId).thenComparingInt(OverviewSet::setNo);
	private final PlayerRepository playerRepository;
	private final PlayerSeasonRepository playerSeasonRepository;
	private final PlayerOverviewRepository overviewRepository;

	public PlayerOverviewResponse getOverview(Long playerId, String patch, Long championId) {
		String normalizedPatch = PlayerQuerySupport.normalizePatch(patch);
		PlayerQuerySupport.validateChampionId(championId);
		Player player = PlayerQuerySupport.requirePlayer(playerRepository, playerId);
		PositionComparison comparison = PositionComparison.load(overviewRepository, player, normalizedPatch, championId);
		List<OverviewSet> own = comparison.own();
		boolean filtered = comparison.filtered();
		boolean eligible = comparison.eligible();
		PlayerSeason season = playerSeasonRepository.findByPlayerId(playerId).orElse(null);
		List<KeyMetric> keyMetrics = comparison.metrics(PlayerMetric.KEY_METRICS).values().stream()
				.map(view -> new KeyMetric(view.metric().name(), view.metric().label(), view.value(),
						view.positionAvg(), view.topPercent(), view.grade(), view.metric().direction()))
				.toList();

		List<RecentSet> recentSets = own.stream().sorted(CHRONOLOGICAL.reversed()).limit(5)
				.map(row -> new RecentSet(row.matchId(), row.setNo(), row.playedAt().toLocalDate(),
						new TeamInfo(row.opponentId(), row.opponentCode()),
						new ChampionInfo(row.championId(), row.championName()), row.kills(), row.deaths(),
						row.assists(), row.win(), Rating.fromPoints(row.totalPoints()))).toList();
		List<RatingPoint> trend = own.stream().sorted(CHRONOLOGICAL)
				.map(row -> new RatingPoint(row.matchId(), row.setNo(), row.playedAt().toLocalDate(),
						Rating.fromPoints(row.totalPoints()), row.pog())).toList();
		return new PlayerOverviewResponse(new CompareGroup(player.getPosition(), comparison.playerCount(), MIN_SETS),
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
					Double rating = Rating.average(rows.stream().map(row -> Rating.fromPoints(row.totalPoints())).toList());
					return new MostChampion(entry.getKey(), rows.getFirst().championName(), rows.size(),
							Decimals.rate(rows.stream().filter(OverviewSet::win).count(), rows.size()),
							Decimals.kda(kills, deaths, assists),
							PlayerMetric.DPM.round(PlayerMetric.DPM.compute(rows, Map.of())),
							rating);
				}).toList();
	}
}
