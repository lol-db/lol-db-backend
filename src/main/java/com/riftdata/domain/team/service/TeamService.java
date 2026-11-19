package com.riftdata.domain.team.service;

import com.riftdata.domain.team.dto.StandingResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse.MainChampion;
import com.riftdata.domain.team.dto.TeamDetailResponse.RecentMatch;
import com.riftdata.domain.team.dto.TeamDetailResponse.RosterPlayer;
import com.riftdata.domain.team.dto.TeamDetailResponse.StatRanks;
import com.riftdata.domain.team.dto.TeamDetailResponse.TeamStats;
import com.riftdata.domain.team.dto.TeamResponse;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.MatchTeamResult;
import com.riftdata.domain.team.repository.RosterRow;
import com.riftdata.domain.team.repository.RosterSetRow;
import com.riftdata.domain.team.repository.SetGoldAt15;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.domain.team.repository.TeamSetSummary;
import com.riftdata.global.common.Rating;
import com.riftdata.global.common.Side;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

	private static final int RECENT_COUNT = 5;

	private final TeamRepository teamRepository;

	public List<TeamResponse> getTeams() {
		return teamRepository.findAllByOrderByCodeAsc().stream().map(TeamResponse::from).toList();
	}

	/**
	 * 정규 시즌 순위표. 저장하지 않고 끝난 경기의 세트 기록으로 매번 계산한다.
	 * 정렬: 경기 승수 → 세트 득실 → 승자승 → 세트 승수 → 팀코드 (화면 설계서 SCR-005).
	 */
	public List<StandingResponse> getStandings() {
		Map<Long, StandingTally> tallies = new LinkedHashMap<>();
		for (Team team : teamRepository.findAllByOrderByCodeAsc()) {
			tallies.put(team.getId(), new StandingTally(team.getId(), team.getCode()));
		}

		List<FinishedMatch> regularMatches = finishedMatches().stream()
				.filter(match -> match.home().regular())
				.sorted(Comparator.comparing(match -> match.home().matchTime()))
				.toList();
		regularMatches.forEach(match -> {
			tallies.get(match.home().teamId()).add(match.home());
			tallies.get(match.away().teamId()).add(match.away());
		});

		// 경기 승수와 세트 득실이 같은 팀끼리 묶는다. 묶음 순서는 승수, 득실 내림차순.
		Map<List<Integer>, List<StandingTally>> tiedGroups = tallies.values().stream()
				.sorted(Comparator.comparingInt(StandingTally::wins).reversed()
						.thenComparing(Comparator.comparingInt(StandingTally::setDiff).reversed()))
				.collect(Collectors.groupingBy(r -> List.of(r.wins(), r.setDiff()),
						LinkedHashMap::new, Collectors.toList()));

		List<StandingTally> sorted = new ArrayList<>();
		for (List<StandingTally> group : tiedGroups.values()) {
			sorted.addAll(breakTie(group, regularMatches));
		}

		List<StandingResponse> standings = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			standings.add(sorted.get(i).toResponse(i + 1));
		}
		return standings;
	}

	/**
	 * 승수·득실이 같은 팀들의 순서. 그 팀들끼리 치른 정규 시즌 경기의 승수(승자승)로 먼저 정한다.
	 * 세 팀 이상이면 묶음 안의 맞대결만 센다. 그래도 같으면(순환 등) 세트 승수 → 팀코드 순이다.
	 */
	private static List<StandingTally> breakTie(List<StandingTally> group, List<FinishedMatch> regularMatches) {
		if (group.size() == 1) {
			return group;
		}
		Set<Long> ids = group.stream().map(r -> r.teamId).collect(Collectors.toSet());
		Map<Long, Integer> headToHeadWins = new HashMap<>();
		for (FinishedMatch match : regularMatches) {
			if (ids.contains(match.home().teamId()) && ids.contains(match.away().teamId())) {
				MatchTeamResult winner = match.home().won() ? match.home() : match.away();
				headToHeadWins.merge(winner.teamId(), 1, Integer::sum);
			}
		}
		return group.stream()
				.sorted(Comparator.comparingInt((StandingTally r) -> headToHeadWins.getOrDefault(r.teamId, 0)).reversed()
						.thenComparing(Comparator.comparingInt((StandingTally r) -> r.setWins).reversed())
						.thenComparing(r -> r.code))
				.toList();
	}

	public TeamDetailResponse getTeam(Long teamId) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));

		StandingResponse standing = getStandings().stream()
				.filter(row -> row.teamId().equals(teamId))
				.findFirst()
				.orElseThrow();

		return new TeamDetailResponse(team.getId(), team.getCode(), standing,
				roster(teamId), stats(teamId), recentMatches(teamId));
	}

	private List<RosterPlayer> roster(Long teamId) {
		Map<Long, List<RosterSetRow>> setsByPlayer = teamRepository.findRosterSets(teamId).stream()
				.collect(Collectors.groupingBy(RosterSetRow::playerId));
		return teamRepository.findRoster(teamId).stream()
				.sorted(Comparator.comparing((RosterRow row) -> row.player().getPosition())
						.thenComparing(row -> row.player().getNickname()))
				.map(row -> rosterPlayer(row, setsByPlayer.getOrDefault(row.player().getId(), List.of())))
				.toList();
	}

	private static RosterPlayer rosterPlayer(RosterRow row, List<RosterSetRow> sets) {
		int kills = sets.stream().mapToInt(RosterSetRow::kills).sum();
		int deaths = sets.stream().mapToInt(RosterSetRow::deaths).sum();
		int assists = sets.stream().mapToInt(RosterSetRow::assists).sum();
		Double kda = sets.isEmpty() ? null : round((kills + assists) / (double) Math.max(deaths, 1), 2);
		OptionalDouble avgRating = sets.stream()
				.map(set -> Rating.fromPoints(set.totalPoints()))
				.filter(Objects::nonNull)
				.mapToDouble(Double::doubleValue)
				.average();
		return new RosterPlayer(
				row.player().getId(),
				row.player().getNickname(),
				row.player().getName(),
				row.player().getPosition(),
				row.season() == null ? null : row.season().getSets(),
				row.season() == null ? null : row.season().getTotalPoints(),
				sets.size(),
				avgRating.isPresent() ? round(avgRating.getAsDouble(), 1) : null,
				kills, deaths, assists, kda,
				mainChampion(sets));
	}

	/** 가장 많이 쓴 챔피언. 세트 수가 같으면 이름순으로 앞선 챔피언. */
	private static MainChampion mainChampion(List<RosterSetRow> sets) {
		return sets.stream()
				.collect(Collectors.groupingBy(RosterSetRow::championId))
				.values().stream()
				.map(picks -> new MainChampion(picks.get(0).championId(), picks.get(0).championName(), picks.size()))
				.min(Comparator.comparingInt(MainChampion::sets).reversed().thenComparing(MainChampion::name))
				.orElse(null);
	}

	/** 팀 지표에 리그 내 순위를 붙인다. 세트 기록이 없는 팀은 지표·순위가 모두 null이다. */
	private TeamStats stats(Long teamId) {
		Map<Long, TeamStats> league = leagueStats();
		TeamStats own = league.get(teamId);
		if (own == null) {
			return new TeamStats(0, null, null, null, null, null, null, null,
					new StatRanks(league.size(), null, null, null, null, null, null, null));
		}
		StatRanks ranks = new StatRanks(league.size(),
				rankOf(teamId, league, TeamStats::goldDiffAt15, true),
				rankOf(teamId, league, TeamStats::firstDragonRate, true),
				rankOf(teamId, league, TeamStats::firstTowerRate, true),
				rankOf(teamId, league, TeamStats::firstBaronRate, true),
				rankOf(teamId, league, TeamStats::avgPlaySeconds, false),
				rankOf(teamId, league, TeamStats::blueWinRate, true),
				rankOf(teamId, league, TeamStats::redWinRate, true));
		return new TeamStats(own.sets(), own.goldDiffAt15(), own.firstDragonRate(), own.firstTowerRate(),
				own.firstBaronRate(), own.avgPlaySeconds(), own.blueWinRate(), own.redWinRate(), ranks);
	}

	/** 세트 기록이 있는 모든 팀의 지표 (순위 없이). 쿼리 두 번으로 리그 전체를 계산한다. */
	private Map<Long, TeamStats> leagueStats() {
		Map<Long, List<Long>> goldDiffs = goldDiffsAt15();
		Map<Long, TeamStats> league = new HashMap<>();
		teamRepository.findAllTeamSetSummaries().stream()
				.collect(Collectors.groupingBy(TeamSetSummary::teamId))
				.forEach((teamId, sets) -> league.put(teamId, teamStats(sets, goldDiffs.get(teamId))));
		return league;
	}

	private static TeamStats teamStats(List<TeamSetSummary> sets, List<Long> goldDiffs) {
		List<TeamSetSummary> blue = sets.stream().filter(s -> s.side() == Side.BLUE).toList();
		List<TeamSetSummary> red = sets.stream().filter(s -> s.side() == Side.RED).toList();
		Integer avgPlaySeconds = (int) Math.round(
				sets.stream().mapToInt(s -> s.playTime().toSecondOfDay()).average().orElseThrow());
		Integer goldDiffAt15 = goldDiffs == null ? null
				: (int) Math.round(goldDiffs.stream().mapToLong(Long::longValue).average().orElseThrow());
		return new TeamStats(
				sets.size(),
				goldDiffAt15,
				rate(sets, TeamSetSummary::firstDragon),
				rate(sets, TeamSetSummary::firstTower),
				rate(sets, TeamSetSummary::firstBaron),
				avgPlaySeconds,
				rate(blue, TeamSetSummary::victory),
				rate(red, TeamSetSummary::victory),
				null);
	}

	/** 팀별 세트당 15분 골드 차 목록. 양 팀 5명 모두 15분 골드가 있는 세트만 쓴다. */
	private Map<Long, List<Long>> goldDiffsAt15() {
		Map<Long, List<Long>> diffs = new HashMap<>();
		teamRepository.findAllGoldAt15().stream()
				.collect(Collectors.groupingBy(SetGoldAt15::setId))
				.values().stream()
				.filter(rows -> rows.size() == 2 && rows.stream().allMatch(r -> r.playersWithData() == 5))
				.forEach(rows -> {
					SetGoldAt15 a = rows.get(0);
					SetGoldAt15 b = rows.get(1);
					diffs.computeIfAbsent(a.teamId(), k -> new ArrayList<>()).add(a.goldAt15() - b.goldAt15());
					diffs.computeIfAbsent(b.teamId(), k -> new ArrayList<>()).add(b.goldAt15() - a.goldAt15());
				});
		return diffs;
	}

	/** 1 + 이 팀보다 좋은 값을 가진 팀 수. 값이 없으면 null. */
	private static Integer rankOf(Long teamId, Map<Long, TeamStats> league,
			Function<TeamStats, Integer> metric, boolean higherIsBetter) {
		Integer own = metric.apply(league.get(teamId));
		if (own == null) {
			return null;
		}
		long better = league.values().stream()
				.map(metric)
				.filter(Objects::nonNull)
				.filter(value -> higherIsBetter ? value > own : value < own)
				.count();
		return (int) better + 1;
	}

	private static double round(double value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
	}

	private List<RecentMatch> recentMatches(Long teamId) {
		return finishedMatches().stream()
				.filter(match -> match.involves(teamId))
				.sorted(Comparator.comparing((FinishedMatch match) -> match.home().matchTime()).reversed())
				.limit(RECENT_COUNT)
				.map(match -> {
					MatchTeamResult own = match.side(teamId);
					MatchTeamResult opponent = match.other(teamId);
					return new RecentMatch(own.matchId(), own.matchTime(), own.stageName(),
							opponent.teamId(), opponent.teamCode(),
							own.setWins().intValue(), opponent.setWins().intValue(), own.won());
				})
				.toList();
	}

	/** 한 팀이 승리 세트 수에 도달한 경기만 끝난 경기로 본다. 진행 중이거나 기록이 한쪽만 있는 경기는 뺀다. */
	private List<FinishedMatch> finishedMatches() {
		Collection<List<MatchTeamResult>> byMatch = teamRepository.findMatchTeamResults().stream()
				.collect(Collectors.groupingBy(MatchTeamResult::matchId, LinkedHashMap::new, Collectors.toList()))
				.values();
		return byMatch.stream()
				.filter(rows -> rows.size() == 2 && (rows.get(0).won() || rows.get(1).won()))
				.map(rows -> new FinishedMatch(rows.get(0), rows.get(1)))
				.toList();
	}

	private static <T> Integer rate(List<T> rows, Function<T, Boolean> flag) {
		if (rows.isEmpty()) {
			return null;
		}
		long count = rows.stream().map(flag).filter(Predicate.isEqual(true)).count();
		return (int) Math.round(count * 100.0 / rows.size());
	}

	private record FinishedMatch(MatchTeamResult home, MatchTeamResult away) {

		boolean involves(Long teamId) {
			return home.teamId().equals(teamId) || away.teamId().equals(teamId);
		}

		MatchTeamResult side(Long teamId) {
			return home.teamId().equals(teamId) ? home : away;
		}

		MatchTeamResult other(Long teamId) {
			return home.teamId().equals(teamId) ? away : home;
		}
	}

	/** 순위표 계산용 팀별 누적값. */
	private static final class StandingTally {

		private final Long teamId;
		private final String code;
		private int wins;
		private int losses;
		private int setWins;
		private int setLosses;
		private final List<String> results = new ArrayList<>();

		StandingTally(Long teamId, String code) {
			this.teamId = teamId;
			this.code = code;
		}

		void add(MatchTeamResult result) {
			boolean won = result.won();
			int lost = (int) (result.setsPlayed() - result.setWins());
			if (won) {
				wins++;
			} else {
				losses++;
			}
			setWins += result.setWins().intValue();
			setLosses += lost;
			results.add(won ? "W" : "L");
		}

		int wins() {
			return wins;
		}

		int setDiff() {
			return setWins - setLosses;
		}

		StandingResponse toResponse(int rank) {
			int matches = wins + losses;
			int winRate = matches == 0 ? 0 : (int) Math.round(wins * 100.0 / matches);
			List<String> recent = results.subList(Math.max(0, results.size() - RECENT_COUNT), results.size());
			return new StandingResponse(rank, teamId, code, matches, wins, losses, setWins, setLosses,
					setDiff(), winRate, List.copyOf(recent));
		}
	}
}
