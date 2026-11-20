package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerChampionsResponse;
import com.riftdata.domain.player.dto.PlayerChampionsResponse.ChampionPool;
import com.riftdata.domain.player.dto.PlayerChampionsResponse.ChampionStat;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.repository.PlayerOverviewRepository;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 선수 챔피언 탭 (API-17). 같은 포지션 비교 집단은 종합 탭과 같다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerChampionsService {

	/** 이 세트 수 미만이면 화면에 "참고용"으로 표시한다. */
	static final int LOW_SAMPLE_SETS = 5;

	private static final Comparator<OverviewSet> CHRONOLOGICAL = Comparator
			.comparing(OverviewSet::playedAt).thenComparing(OverviewSet::matchId).thenComparingInt(OverviewSet::setNo);

	private final PlayerRepository playerRepository;
	private final PlayerOverviewRepository overviewRepository;

	public PlayerChampionsResponse getChampions(Long playerId, String sort, String patch, Long championId) {
		Comparator<ChampionStat> order = order(sort);
		String normalizedPatch = PlayerQuerySupport.normalizePatch(patch);
		PlayerQuerySupport.validateChampionId(championId);
		Player player = PlayerQuerySupport.requirePlayer(playerRepository, playerId);
		PositionComparison comparison = PositionComparison.load(overviewRepository, player, normalizedPatch, championId);

		List<OverviewSet> own = comparison.own();
		List<ChampionStat> champions = own.stream()
				.collect(Collectors.groupingBy(OverviewSet::championId)).values().stream()
				.map(PlayerChampionsService::stat)
				.sorted(order)
				.toList();
		int mostPlayed = champions.stream().mapToInt(ChampionStat::sets).max().orElse(0);
		List<Double> poolSizes = comparison.comparable().values().stream()
				.map(rows -> (double) rows.stream().map(OverviewSet::championId).distinct().count())
				.toList();
		ChampionPool pool = new ChampionPool(champions.size(),
				own.isEmpty() ? null : Decimals.rate(mostPlayed, own.size()),
				Decimals.stat(PlayerMetric.mean(poolSizes)));
		return new PlayerChampionsResponse(pool, champions);
	}

	private static ChampionStat stat(List<OverviewSet> rows) {
		OverviewSet first = rows.getFirst();
		int wins = (int) rows.stream().filter(OverviewSet::win).count();
		int kills = rows.stream().mapToInt(OverviewSet::kills).sum();
		int deaths = rows.stream().mapToInt(OverviewSet::deaths).sum();
		int assists = rows.stream().mapToInt(OverviewSet::assists).sum();
		List<Double> ratings = rows.stream().sorted(CHRONOLOGICAL)
				.map(row -> Rating.fromPoints(row.totalPoints())).filter(Objects::nonNull).toList();
		return new ChampionStat(first.championId(), first.championName(), rows.size(), wins, rows.size() - wins,
				Decimals.rate(wins, rows.size()), kills, deaths, assists, Decimals.kda(kills, deaths, assists),
				PlayerMetric.DPM.round(PlayerMetric.DPM.compute(rows, Map.of())),
				PlayerMetric.GD15.round(PlayerMetric.GD15.compute(rows, Map.of())),
				Rating.average(ratings), rows.size() < LOW_SAMPLE_SETS, ratings);
	}

	/** sets(기본) · winRate · avgRating 내림차순. 값이 없으면 뒤로, 같으면 세트 수 → 챔피언 ID */
	private static Comparator<ChampionStat> order(String sort) {
		Comparator<ChampionStat> primary = switch (sort == null || sort.isBlank() ? "sets" : sort.strip()) {
			case "sets" -> Comparator.comparingInt(ChampionStat::sets).reversed();
			case "winRate" -> Comparator.comparing(ChampionStat::winRate,
					Comparator.nullsLast(Comparator.reverseOrder()));
			case "avgRating" -> Comparator.comparing(ChampionStat::avgRating,
					Comparator.nullsLast(Comparator.reverseOrder()));
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
		};
		return primary.thenComparing(Comparator.comparingInt(ChampionStat::sets).reversed())
				.thenComparing(ChampionStat::championId);
	}
}
