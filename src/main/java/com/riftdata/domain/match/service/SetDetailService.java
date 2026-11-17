package com.riftdata.domain.match.service;

import com.riftdata.domain.match.dto.response.ChampionResponse;
import com.riftdata.domain.match.dto.response.SetDetailResponse;
import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.match.repository.LckMatchRepository;
import com.riftdata.domain.match.repository.MatchSetRepository;
import com.riftdata.domain.record.entity.Ban;
import com.riftdata.domain.record.entity.PlayerSet;
import com.riftdata.domain.record.entity.TeamSet;
import com.riftdata.domain.record.repository.BanRepository;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.record.repository.TeamSetRepository;
import com.riftdata.global.common.Rating;
import com.riftdata.global.common.Side;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 세트 상세 (API-07) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SetDetailService {

	/** Oracle's Elixir에 있는 시점별 골드 (분) */
	private static final int[] GOLD_MINUTES = {10, 15, 20, 25};

	private final LckMatchRepository lckMatchRepository;
	private final MatchSetRepository matchSetRepository;
	private final TeamSetRepository teamSetRepository;
	private final PlayerSetRepository playerSetRepository;
	private final BanRepository banRepository;

	/** 경기가 없으면 404 MATCH_NOT_FOUND, 경기는 있는데 그 세트가 없으면 404 SET_NOT_FOUND */
	public SetDetailResponse getSet(Long matchId, Integer setNo) {
		MatchSet set = matchSetRepository.findByMatchIdAndSetNumber(matchId, setNo)
				.orElseThrow(() -> new BusinessException(lckMatchRepository.existsById(matchId)
						? ErrorCode.SET_NOT_FOUND : ErrorCode.MATCH_NOT_FOUND));

		List<TeamSet> teamSets = teamSetRepository.findWithTeamByMatchSetId(set.getId()).stream()
				.sorted(Comparator.comparing(TeamSet::getSide))
				.toList();
		Map<Long, List<PlayerSet>> playersByTeamSet = playerSetRepository.findScoreboardByMatchSetId(set.getId())
				.stream()
				.sorted(Comparator.comparing(PlayerSet::getPosition))
				.collect(Collectors.groupingBy(ps -> ps.getTeamSet().getId()));
		Map<Long, List<Ban>> bansByTeamSet = banRepository.findWithChampionByMatchSetId(set.getId()).stream()
				.collect(Collectors.groupingBy(b -> b.getTeamSet().getId()));

		int durationSec = set.getPlaySeconds();
		List<SetDetailResponse.TeamDetail> teams = teamSets.stream()
				.map(ts -> teamDetail(ts,
						playersByTeamSet.getOrDefault(ts.getId(), List.of()),
						bansByTeamSet.getOrDefault(ts.getId(), List.of()),
						durationSec))
				.toList();
		Long winnerTeamId = teamSets.stream()
				.filter(TeamSet::getVictory)
				.map(ts -> ts.getTeam().getId())
				.findFirst()
				.orElse(null);

		Map<Side, List<PlayerSet>> playersBySide = teamSets.stream()
				.collect(Collectors.toMap(TeamSet::getSide,
						ts -> playersByTeamSet.getOrDefault(ts.getId(), List.of())));

		return new SetDetailResponse(
				matchId,
				set.getSetNumber(),
				set.getGameId(),
				set.getVersion(),
				durationSec,
				winnerTeamId,
				teams,
				goldDiff(playersBySide.getOrDefault(Side.BLUE, List.of()),
						playersBySide.getOrDefault(Side.RED, List.of())));
	}

	private static SetDetailResponse.TeamDetail teamDetail(
			TeamSet teamSet, List<PlayerSet> players, List<Ban> bans, int durationSec) {
		int teamDamage = players.stream().mapToInt(PlayerSet::getDamageToChampions).sum();

		List<SetDetailResponse.BanResponse> banResponses = bans.stream()
				.map(b -> new SetDetailResponse.BanResponse(
						b.getBanOrder(), b.getChampion().getId(), b.getChampion().getName()))
				.toList();
		List<SetDetailResponse.PickResponse> picks = players.stream()
				.map(ps -> new SetDetailResponse.PickResponse(
						ps.getChampion().getId(), ps.getChampion().getName(), ps.getPosition()))
				.toList();
		SetDetailResponse.Objectives objectives = new SetDetailResponse.Objectives(
				players.stream().mapToInt(PlayerSet::getKills).sum(),
				players.stream().mapToInt(PlayerSet::getTotalGolds).sum(),
				teamSet.getTowers(),
				teamSet.getDragons(),
				teamSet.getVoidGrubs(),
				teamSet.getBarons(),
				teamSet.getInhibitors());
		List<SetDetailResponse.PlayerLine> lines = players.stream()
				.map(ps -> playerLine(ps, teamDamage, durationSec))
				.toList();

		return new SetDetailResponse.TeamDetail(
				teamSet.getTeam().getId(),
				teamSet.getTeam().getCode(),
				teamSet.getSide(),
				Boolean.TRUE.equals(teamSet.getVictory()),
				banResponses,
				picks,
				objectives,
				lines);
	}

	private static SetDetailResponse.PlayerLine playerLine(PlayerSet ps, int teamDamage, int durationSec) {
		int damage = ps.getDamageToChampions();
		return new SetDetailResponse.PlayerLine(
				ps.getPlayer().getId(),
				ps.getPlayer().getNickname(),
				ps.getPosition(),
				new ChampionResponse(ps.getChampion().getId(), ps.getChampion().getName()),
				ps.getKills(),
				ps.getDeaths(),
				ps.getAssists(),
				ps.getMinionKills() + ps.getMonsterKills(),
				ps.getTotalGolds(),
				durationSec > 0 ? (int) Math.round(damage * 60.0 / durationSec) : null,
				teamDamage > 0 ? round3((double) damage / teamDamage) : null,
				ps.getVisionPoints(),
				Rating.fromPoints(ps.getTotalPoints()),
				Boolean.TRUE.equals(ps.getPog()));
	}

	/**
	 * 10·15·20·25분 골드 차 (블루 − 레드). 경기가 그 시점 전에 끝났거나 값이 빠진 선수가 있으면
	 * 그 시점은 뺀다.
	 */
	static List<SetDetailResponse.GoldDiff> goldDiff(List<PlayerSet> blue, List<PlayerSet> red) {
		List<SetDetailResponse.GoldDiff> diffs = new ArrayList<>();
		if (blue.isEmpty() || red.isEmpty()) {
			return diffs;
		}
		for (int minute : GOLD_MINUTES) {
			Function<PlayerSet, Integer> goldAt = goldAt(minute);
			Integer blueGold = sum(blue, goldAt);
			Integer redGold = sum(red, goldAt);
			if (blueGold != null && redGold != null) {
				diffs.add(new SetDetailResponse.GoldDiff(minute, blueGold - redGold));
			}
		}
		return diffs;
	}

	private static Function<PlayerSet, Integer> goldAt(int minute) {
		return switch (minute) {
			case 10 -> PlayerSet::getGoldAt10;
			case 15 -> PlayerSet::getGoldAt15;
			case 20 -> PlayerSet::getGoldAt20;
			case 25 -> PlayerSet::getGoldAt25;
			default -> throw new IllegalArgumentException("골드 기록이 없는 시점: " + minute);
		};
	}

	/** 값이 하나라도 null이면 null */
	private static Integer sum(List<PlayerSet> players, Function<PlayerSet, Integer> value) {
		int total = 0;
		for (PlayerSet ps : players) {
			Integer v = value.apply(ps);
			if (v == null) {
				return null;
			}
			total += v;
		}
		return total;
	}

	private static double round3(double value) {
		return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
	}
}
