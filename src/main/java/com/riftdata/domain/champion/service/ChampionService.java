package com.riftdata.domain.champion.service;

import com.riftdata.domain.champion.dto.ChampionPlayerResponse;
import com.riftdata.domain.champion.dto.ChampionStatsResponse;
import com.riftdata.domain.champion.dto.ChampionStatsResponse.ChampionStat;
import com.riftdata.domain.champion.dto.ChampionStatsResponse.TopPlayer;
import com.riftdata.domain.champion.repository.ChampionRepository;
import com.riftdata.domain.champion.repository.ChampionStatsRepository;
import com.riftdata.domain.champion.repository.ChampionStatsRepository.BanCount;
import com.riftdata.domain.champion.repository.ChampionStatsRepository.Pick;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 챔피언 통계(API-19)와 챔피언을 잘 쓰는 선수(API-20). 2026 시즌 기록 기준. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChampionService {

	/** API-20: 이 세트 수 이상 쓴 선수만 */
	static final int MIN_PLAYER_SETS = 3;

	private final ChampionRepository championRepository;
	private final ChampionStatsRepository statsRepository;

	public ChampionStatsResponse getChampions(String patch, Position position) {
		String normalizedPatch = normalizePatch(patch);
		long totalSets = statsRepository.countSets(normalizedPatch);
		Map<Long, List<Pick>> picksByChampion = statsRepository.findPicks(normalizedPatch, position, null).stream()
				.collect(Collectors.groupingBy(Pick::championId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, BanCount> bans = new HashMap<>();
		statsRepository.countBans(normalizedPatch).forEach(ban -> bans.put(ban.championId(), ban));

		// 포지션 필터가 있으면 그 포지션에서 픽된 챔피언만, 없으면 밴만 된 챔피언도 포함한다.
		Map<Long, String> names = new LinkedHashMap<>();
		picksByChampion.forEach((id, picks) -> names.put(id, picks.getFirst().championName()));
		if (position == null) {
			bans.values().forEach(ban -> names.putIfAbsent(ban.championId(), ban.championName()));
		}
		List<ChampionStat> champions = names.entrySet().stream()
				.map(entry -> stat(entry.getKey(), entry.getValue(), picksByChampion.getOrDefault(entry.getKey(), List.of()),
						bans.containsKey(entry.getKey()) ? bans.get(entry.getKey()).bans() : 0, totalSets))
				.sorted(Comparator.comparing(ChampionStat::pickBanRate, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Comparator.comparingLong(ChampionStat::picks).reversed())
						.thenComparing(ChampionStat::name))
				.toList();
		return new ChampionStatsResponse(totalSets, champions);
	}

	public List<ChampionPlayerResponse> getChampionPlayers(Long championId) {
		if (championId == null || championId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (!championRepository.existsById(championId)) {
			throw new BusinessException(ErrorCode.CHAMPION_NOT_FOUND);
		}
		return statsRepository.findPicks(null, null, championId).stream()
				.collect(Collectors.groupingBy(Pick::playerId)).values().stream()
				.filter(picks -> picks.size() >= MIN_PLAYER_SETS)
				.map(picks -> {
					Pick first = picks.getFirst();
					return new ChampionPlayerResponse(first.playerId(), first.nickname(), first.teamCode(),
							picks.size(), winRate(picks), kda(picks), avgRating(picks));
				})
				.sorted(Comparator.comparing(ChampionPlayerResponse::avgRating,
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Comparator.comparingInt(ChampionPlayerResponse::sets).reversed())
						.thenComparing(ChampionPlayerResponse::name))
				.toList();
	}

	private static ChampionStat stat(Long championId, String name, List<Pick> picks, long bans, long totalSets) {
		return new ChampionStat(championId, name, mainPosition(picks), picks.size(), bans,
				Decimals.rate(picks.size() + bans, totalSets),
				picks.isEmpty() ? null : winRate(picks),
				picks.isEmpty() ? null : kda(picks),
				avgRating(picks), topPlayer(picks));
	}

	private static Position mainPosition(List<Pick> picks) {
		return picks.stream().collect(Collectors.groupingBy(Pick::position, Collectors.counting()))
				.entrySet().stream()
				.min(Comparator.<Map.Entry<Position, Long>>comparingLong(Map.Entry::getValue).reversed()
						.thenComparing(Map.Entry::getKey))
				.map(Map.Entry::getKey).orElse(null);
	}

	private static TopPlayer topPlayer(List<Pick> picks) {
		return picks.stream().collect(Collectors.groupingBy(Pick::playerId)).values().stream()
				.min(Comparator.<List<Pick>>comparingInt(List::size).reversed()
						.thenComparing(ChampionService::avgRating, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(rows -> rows.getFirst().playerId()))
				.map(rows -> new TopPlayer(rows.getFirst().playerId(), rows.getFirst().nickname(), rows.size()))
				.orElse(null);
	}

	private static Double winRate(List<Pick> picks) {
		return Decimals.rate(picks.stream().filter(Pick::win).count(), picks.size());
	}

	private static Double kda(List<Pick> picks) {
		return Decimals.kda(picks.stream().mapToLong(Pick::kills).sum(), picks.stream().mapToLong(Pick::deaths).sum(),
				picks.stream().mapToLong(Pick::assists).sum());
	}

	private static Double avgRating(List<Pick> picks) {
		return Rating.average(picks.stream().map(pick -> Rating.fromPoints(pick.totalPoints())).toList());
	}

	/** 선수 API와 같은 패치 검사: 앞뒤 공백 제거, 빈 값은 필터 없음, 15자(컬럼 길이) 초과는 400 */
	private static String normalizePatch(String patch) {
		if (patch == null || patch.isBlank()) return null;
		String normalized = patch.strip();
		if (normalized.length() > 15 || normalized.chars().anyMatch(Character::isISOControl)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return normalized;
	}
}
