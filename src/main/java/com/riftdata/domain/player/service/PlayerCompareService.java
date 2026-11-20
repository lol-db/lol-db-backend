package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerCompareResponse;
import com.riftdata.domain.player.dto.PlayerCompareResponse.AreaScore;
import com.riftdata.domain.player.dto.PlayerCompareResponse.ComparedMetric;
import com.riftdata.domain.player.dto.PlayerCompareResponse.ComparedPlayer;
import com.riftdata.domain.player.dto.PlayerCompareResponse.HeadToHead;
import com.riftdata.domain.player.dto.PlayerCompareResponse.HeadToHeadSet;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.repository.PlayerOverviewRepository;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.TeamTotals;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 선수 비교 (API-18). 2026 시즌 전체 기록으로 비교한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerCompareService {

	/** 비교 지표: KDA + 종합 탭 핵심 지표 10개 */
	static final List<PlayerMetric> METRICS = Stream.concat(Stream.of(PlayerMetric.KDA),
			PlayerMetric.KEY_METRICS.stream()).toList();

	/** 시즌 점수를 보여 주는 최소 출전 세트 (명세서 1.4) */
	private static final int MIN_SETS = 10;

	private final PlayerRepository playerRepository;
	private final PlayerSeasonRepository playerSeasonRepository;
	private final PlayerOverviewRepository overviewRepository;

	public PlayerCompareResponse compare(Long p1, Long p2) {
		if (p1 == null || p2 == null || p1.equals(p2)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		Player first = PlayerQuerySupport.requirePlayer(playerRepository, p1);
		Player second = PlayerQuerySupport.requirePlayer(playerRepository, p2);
		boolean samePosition = first.getPosition() == second.getPosition();

		List<OverviewSet> firstSets = overviewRepository.findPlayerSets(p1, null, null);
		List<OverviewSet> secondSets = overviewRepository.findPlayerSets(p2, null, null);
		Map<Long, TeamTotals> teamTotals = overviewRepository.findTeamTotals(
				Stream.concat(firstSets.stream(), secondSets.stream()).map(OverviewSet::teamSetId).distinct().toList());

		List<ComparedPlayer> players = List.of(
				player(first, firstSets, samePosition), player(second, secondSets, samePosition));
		Map<PlayerMetric, Double> firstValues = PlayerMetric.compute(METRICS, firstSets, teamTotals);
		Map<PlayerMetric, Double> secondValues = PlayerMetric.compute(METRICS, secondSets, teamTotals);
		List<ComparedMetric> metrics = METRICS.stream().map(metric -> {
			Double a = metric.round(firstValues.get(metric));
			Double b = metric.round(secondValues.get(metric));
			return new ComparedMetric(metric.name(), metric.label(), Arrays.asList(a, b), metric.direction(),
					better(a, b, metric.direction()));
		}).toList();
		return new PlayerCompareResponse(samePosition, players, metrics, headToHead(first, second, firstSets, secondSets));
	}

	private ComparedPlayer player(Player player, List<OverviewSet> sets, boolean samePosition) {
		PlayerSeason season = sets.size() >= MIN_SETS
				? playerSeasonRepository.findByPlayerId(player.getId())
						.filter(saved -> saved.getSets() >= MIN_SETS).orElse(null)
				: null;
		Integer overall = season == null || PlayerGrade.fromPoints(season.getTotalPoints()) == null
				? null : season.getTotalPoints();
		List<AreaScore> areas = Arrays.stream(PlayerArea.values()).map(area -> {
			Integer points = area.points(season);
			return new AreaScore(area, points == null ? null : points.doubleValue(),
					samePosition ? PlayerGrade.fromPoints(points) : null);
		}).toList();
		long wins = sets.stream().filter(OverviewSet::win).count();
		return new ComparedPlayer(player.getId(), player.getNickname(), player.getTeam().getCode(),
				player.getPosition(), sets.size(), Decimals.rate(wins, sets.size()),
				overall == null ? null : overall.doubleValue(),
				samePosition ? PlayerGrade.fromPoints(overall) : null, areas);
	}

	/** 더 좋은 쪽 인덱스. direction이 -1이면 작은 값이 좋다. */
	static Integer better(Double a, Double b, int direction) {
		if (a == null || b == null) return null;
		int comparison = Double.compare(a, b) * direction;
		return comparison == 0 ? null : comparison > 0 ? 0 : 1;
	}

	/** 같은 경기·세트 번호에 서로 다른 팀세트로 나온 기록 */
	private static HeadToHead headToHead(Player first, Player second, List<OverviewSet> firstSets,
			List<OverviewSet> secondSets) {
		Map<String, OverviewSet> secondBySet = secondSets.stream()
				.collect(Collectors.toMap(PlayerCompareService::setKey, Function.identity()));
		List<HeadToHeadSet> sets = new ArrayList<>();
		int firstWins = 0;
		int secondWins = 0;
		List<OverviewSet> ordered = firstSets.stream().sorted(Comparator.comparing(OverviewSet::playedAt)
				.thenComparing(OverviewSet::matchId).thenComparingInt(OverviewSet::setNo)).toList();
		for (OverviewSet a : ordered) {
			OverviewSet b = secondBySet.get(setKey(a));
			if (b == null || b.teamSetId().equals(a.teamSetId())) {
				continue;
			}
			if (a.win()) firstWins++;
			if (b.win()) secondWins++;
			boolean laneOpponents = second.getId().equals(a.enemyPlayerId());
			sets.add(new HeadToHeadSet(a.matchId(), a.setNo(), a.playedAt().toLocalDate(),
					List.of(a.championName(), b.championName()),
					Arrays.asList(Rating.fromPoints(a.totalPoints()), Rating.fromPoints(b.totalPoints())),
					laneOpponents ? Decimals.stat(a.goldDiff15()) : null,
					a.win() ? first.getId() : b.win() ? second.getId() : null));
		}
		return new HeadToHead(List.of(firstWins, secondWins), sets);
	}

	private static String setKey(OverviewSet set) {
		return set.matchId() + "-" + set.setNo();
	}
}
