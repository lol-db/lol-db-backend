package com.riftdata.domain.team.service;

import com.riftdata.domain.team.config.SeasonSettings;
import com.riftdata.domain.team.dto.LeaderResponse;
import com.riftdata.domain.team.repository.LeaderSetRow;
import com.riftdata.domain.team.repository.MetaQueryRepository;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Rating;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지표 리더 (API-09). 전체 포지션, 세트 기록이 minSetsForGrade(기본 10) 이상인 선수 가운데 지표마다 1위.
 * 대상: 플레이오프를 포함한 모든 세트 기록. 같은 값이면 세트 수가 많은 선수, 그다음 닉네임순.
 * DPM·VSPM은 선수 API와 같이 세트별 분당 값의 평균이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderService {

	private final MetaQueryRepository metaQueryRepository;
	private final SeasonSettings seasonSettings;

	public List<LeaderResponse> getLeaders() {
		List<LeaderSetRow> rows = metaQueryRepository.findLeaderSetRows();
		// 세트·포지션마다 팀별 15분 골드. 상대 라이너(같은 세트, 같은 포지션, 다른 팀)를 바로 찾는다.
		Map<LaneKey, Map<Long, Integer>> goldAt15 = new HashMap<>();
		for (LeaderSetRow row : rows) {
			if (row.goldAt15() != null) {
				goldAt15.computeIfAbsent(new LaneKey(row.setId(), row.position()), k -> new HashMap<>())
						.put(row.teamId(), row.goldAt15());
			}
		}
		List<PlayerTotals> eligible = rows.stream()
				.collect(Collectors.groupingBy(LeaderSetRow::playerId))
				.values().stream()
				.filter(sets -> sets.size() >= seasonSettings.getMinSetsForGrade())
				.map(sets -> PlayerTotals.of(sets, goldAt15))
				.toList();

		List<LeaderResponse> leaders = new ArrayList<>();
		leader(eligible, "KDA", "KDA", PlayerTotals::kda).ifPresent(leaders::add);
		leader(eligible, "DPM", "분당 피해량", PlayerTotals::dpm).ifPresent(leaders::add);
		leader(eligible, "GD15", "15분 골드 차", PlayerTotals::goldDiffAt15).ifPresent(leaders::add);
		leader(eligible, "VSPM", "분당 시야 점수", PlayerTotals::vspm).ifPresent(leaders::add);
		leader(eligible, "AVG_RATING", "평균 평점", PlayerTotals::avgRating).ifPresent(leaders::add);
		return leaders;
	}

	private static Optional<LeaderResponse> leader(List<PlayerTotals> players, String metric, String label,
			Function<PlayerTotals, Number> value) {
		return players.stream()
				.filter(p -> value.apply(p) != null)
				.min(Comparator.comparingDouble((PlayerTotals p) -> value.apply(p).doubleValue()).reversed()
						.thenComparing(Comparator.comparingInt(PlayerTotals::sets).reversed())
						.thenComparing(PlayerTotals::nickname))
				.map(p -> new LeaderResponse(metric, label, p.playerId(), p.nickname(), p.teamCode(), value.apply(p)));
	}

	/** 한 세트의 한 포지션 (양 팀 라이너). */
	private record LaneKey(Long setId, Position position) {
	}

	private record PlayerTotals(
			Long playerId,
			String nickname,
			String teamCode,
			int sets,
			Double kda,
			Double dpm,
			Double goldDiffAt15,
			Double vspm,
			Double avgRating) {

		static PlayerTotals of(List<LeaderSetRow> sets, Map<LaneKey, Map<Long, Integer>> goldAt15) {
			LeaderSetRow first = sets.get(0);
			int kills = sets.stream().mapToInt(LeaderSetRow::kills).sum();
			int deaths = sets.stream().mapToInt(LeaderSetRow::deaths).sum();
			int assists = sets.stream().mapToInt(LeaderSetRow::assists).sum();

			// 15분 골드 차: 같은 세트 상대 팀의 같은 포지션과 비교. 둘 다 값이 있는 세트만 평균한다.
			List<Integer> diffs = sets.stream()
					.map(s -> goldDiff(s, goldAt15))
					.filter(Objects::nonNull)
					.toList();
			Double avgRating = Rating.average(sets.stream().map(s -> Rating.fromPoints(s.totalPoints())).toList());

			return new PlayerTotals(
					first.playerId(),
					first.nickname(),
					first.teamCode(),
					sets.size(),
					Decimals.kda(kills, deaths, assists),
					perMinuteMean(sets, LeaderSetRow::damageToChampions),
					diffs.isEmpty() ? null : Decimals.stat(diffs.stream().mapToInt(Integer::intValue).average().orElseThrow()),
					perMinuteMean(sets, LeaderSetRow::visionPoints),
					avgRating);
		}

		/**
		 * 세트마다 (값 × 60 ÷ 경기 시간(초))를 구해 평균한다. 선수 API의 DPM·VSPM(PlayerMetric)과 같은 방식이다.
		 * 경기 시간이 0인 세트는 빼고, 남는 세트가 없으면 null.
		 */
		private static Double perMinuteMean(List<LeaderSetRow> sets, ToLongFunction<LeaderSetRow> value) {
			OptionalDouble mean = sets.stream()
					.filter(s -> s.playTime().toSecondOfDay() > 0)
					.mapToDouble(s -> value.applyAsLong(s) * 60.0 / s.playTime().toSecondOfDay())
					.average();
			return mean.isPresent() ? Decimals.stat(mean.getAsDouble()) : null;
		}

		private static Integer goldDiff(LeaderSetRow set, Map<LaneKey, Map<Long, Integer>> goldAt15) {
			if (set.goldAt15() == null) {
				return null;
			}
			return goldAt15.getOrDefault(new LaneKey(set.setId(), set.position()), Map.of()).entrySet().stream()
					.filter(e -> !e.getKey().equals(set.teamId()))
					.map(e -> set.goldAt15() - e.getValue())
					.findFirst()
					.orElse(null);
		}
	}
}
