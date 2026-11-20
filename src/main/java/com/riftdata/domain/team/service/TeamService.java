package com.riftdata.domain.team.service;

import com.riftdata.domain.player.service.PlayerSeasonReader;
import com.riftdata.domain.team.config.SeasonSettings;
import com.riftdata.domain.team.dto.StandingResponse;
import com.riftdata.domain.team.dto.StandingsResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse.MainChampion;
import com.riftdata.domain.team.dto.TeamDetailResponse.Opponent;
import com.riftdata.domain.team.dto.TeamDetailResponse.RecentMatch;
import com.riftdata.domain.team.dto.TeamDetailResponse.RosterPlayer;
import com.riftdata.domain.team.dto.TeamDetailResponse.TeamStat;
import com.riftdata.domain.team.dto.TeamResponse;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.MatchTeamResult;
import com.riftdata.domain.team.repository.RosterRow;
import com.riftdata.domain.team.repository.RosterSetRow;
import com.riftdata.domain.team.repository.SetGoldAt15;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.domain.team.repository.TeamSetSummary;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Rating;
import com.riftdata.global.common.Side;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀 목록(API-10), 순위표(API-08), 팀 상세(API-11). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

	private static final int RECENT_COUNT = 5;

	private final TeamRepository teamRepository;
	private final SeasonSettings seasonSettings;
	private final PlayerSeasonReader playerSeasonReader;

	public List<TeamResponse> getTeams() {
		return teamRepository.findAllByOrderByCodeAsc().stream().map(TeamResponse::from).toList();
	}

	/** 순위표 (API-08). asOf는 집계에 들어간 마지막 경기 날짜다. */
	public StandingsResponse getStandingsTable() {
		List<FinishedMatch> regularMatches = regularMatches();
		LocalDate asOf = regularMatches.isEmpty() ? null
				: regularMatches.getLast().home().matchTime().toLocalDate();
		return new StandingsResponse(asOf, seasonSettings.getPlayoffTeamCount(), standings(regularMatches));
	}

	/** 순위표 행만. 팀 상세와 다른 도메인(TeamStandingReader)이 같은 계산을 쓴다. */
	public List<StandingResponse> getStandings() {
		return standings(regularMatches());
	}

	/**
	 * 정규 시즌 순위. 저장하지 않고 끝난 경기의 세트 기록으로 매번 계산한다.
	 * 순위: 경기 승수 → 세트 득실 → 승자승. 승자승까지 같으면 같은 순위이고, 화면 순서는 팀코드순이다.
	 */
	private List<StandingResponse> standings(List<FinishedMatch> regularMatches) {
		Map<Long, StandingTally> tallies = new LinkedHashMap<>();
		for (Team team : teamRepository.findAllByOrderByCodeAsc()) {
			tallies.put(team.getId(), new StandingTally(team.getId(), team.getCode()));
		}
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

		List<StandingResponse> rows = new ArrayList<>();
		int position = 0;
		for (List<StandingTally> group : tiedGroups.values()) {
			Map<Long, Integer> headToHead = headToHeadWins(group, regularMatches);
			List<StandingTally> ordered = group.stream()
					.sorted(Comparator.comparingInt((StandingTally r) -> headToHead.getOrDefault(r.teamId, 0)).reversed()
							.thenComparing(r -> r.code))
					.toList();
			int rank = 0;
			Integer previousWins = null;
			for (StandingTally tally : ordered) {
				position++;
				int wins = headToHead.getOrDefault(tally.teamId, 0);
				if (!Integer.valueOf(wins).equals(previousWins)) {
					rank = position;
					previousWins = wins;
				}
				rows.add(tally.toResponse(rank));
			}
		}
		return rows;
	}

	/** 묶음 안 팀들끼리 치른 정규 시즌 경기의 승수 (승자승). 세 팀 이상이면 묶음 안 맞대결만 센다. */
	private static Map<Long, Integer> headToHeadWins(List<StandingTally> group, List<FinishedMatch> regularMatches) {
		Map<Long, Integer> wins = new HashMap<>();
		if (group.size() == 1) {
			return wins;
		}
		Set<Long> ids = group.stream().map(r -> r.teamId).collect(Collectors.toSet());
		for (FinishedMatch match : regularMatches) {
			if (ids.contains(match.home().teamId()) && ids.contains(match.away().teamId())) {
				MatchTeamResult winner = match.home().won() ? match.home() : match.away();
				wins.merge(winner.teamId(), 1, Integer::sum);
			}
		}
		return wins;
	}

	public TeamDetailResponse getTeam(Long teamId) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));

		StandingResponse standing = getStandings().stream()
				.filter(row -> row.teamId().equals(teamId))
				.findFirst()
				.orElseThrow();

		return new TeamDetailResponse(team.getId(), team.getCode(),
				standing.rank(), standing.wins(), standing.losses(), standing.setWins(), standing.setLosses(),
				roster(teamId), teamStats(teamId), recentMatches(teamId));
	}

	/** 로스터. 시즌 등급은 선수 도메인(PlayerSeasonReader)에서 로스터 전체를 한 번에 받는다. */
	private List<RosterPlayer> roster(Long teamId) {
		Map<Long, List<RosterSetRow>> setsByPlayer = teamRepository.findRosterSets(teamId).stream()
				.collect(Collectors.groupingBy(RosterSetRow::playerId));
		List<RosterRow> rows = teamRepository.findRoster(teamId);
		Map<Long, String> grades = playerSeasonReader.findSeasonGrades(
				rows.stream().map(row -> row.player().getId()).toList());
		return rows.stream()
				.sorted(Comparator.comparing((RosterRow row) -> row.player().getPosition())
						.thenComparing(row -> row.player().getNickname()))
				.map(row -> rosterPlayer(row, setsByPlayer.getOrDefault(row.player().getId(), List.of()),
						grades.get(row.player().getId())))
				.toList();
	}

	private static RosterPlayer rosterPlayer(RosterRow row, List<RosterSetRow> sets, String grade) {
		int kills = sets.stream().mapToInt(RosterSetRow::kills).sum();
		int deaths = sets.stream().mapToInt(RosterSetRow::deaths).sum();
		int assists = sets.stream().mapToInt(RosterSetRow::assists).sum();
		Double kda = sets.isEmpty() ? null : Decimals.kda(kills, deaths, assists);
		Double avgRating = Rating.average(sets.stream().map(set -> Rating.fromPoints(set.totalPoints())).toList());
		return new RosterPlayer(
				row.player().getId(),
				row.player().getNickname(),
				row.player().getName(),
				row.player().getPosition(),
				grade,
				sets.size(),
				avgRating,
				kda,
				mainChampion(sets));
	}

	/** 가장 많이 쓴 챔피언. 세트 수가 같으면 이름순으로 앞선 챔피언. */
	private static MainChampion mainChampion(List<RosterSetRow> sets) {
		return sets.stream()
				.collect(Collectors.groupingBy(RosterSetRow::championId))
				.values().stream()
				.min(Comparator.comparingInt((List<RosterSetRow> picks) -> picks.size()).reversed()
						.thenComparing(picks -> picks.get(0).championName()))
				.map(picks -> new MainChampion(picks.get(0).championId(), picks.get(0).championName()))
				.orElse(null);
	}

	/**
	 * 팀 지표 목록 (화면 표시 순). 골드 차와 첫 오브젝트 획득률은 리그 내 순위를 붙이고,
	 * 진영 승률과 평균 경기 시간은 높고 낮음이 좋고 나쁨이 아니라서 순위를 매기지 않는다.
	 */
	private List<TeamStat> teamStats(Long teamId) {
		Map<Long, StatValues> league = leagueStats();
		StatValues own = league.getOrDefault(teamId, StatValues.EMPTY);
		return List.of(
				new TeamStat("GD15", "15분 골드 차", own.goldDiffAt15(),
						rankOf(own.goldDiffAt15(), league, StatValues::goldDiffAt15)),
				new TeamStat("FIRST_DRAGON_RATE", "첫 드래곤 획득률", own.firstDragonRate(),
						rankOf(own.firstDragonRate(), league, StatValues::firstDragonRate)),
				new TeamStat("FIRST_TOWER_RATE", "첫 포탑 획득률", own.firstTowerRate(),
						rankOf(own.firstTowerRate(), league, StatValues::firstTowerRate)),
				new TeamStat("FIRST_BARON_RATE", "첫 바론 획득률", own.firstBaronRate(),
						rankOf(own.firstBaronRate(), league, StatValues::firstBaronRate)),
				new TeamStat("AVG_DURATION_SEC", "평균 경기 시간", own.avgDurationSec(), null),
				new TeamStat("BLUE_WIN_RATE", "블루 승률", own.blueWinRate(), null),
				new TeamStat("RED_WIN_RATE", "레드 승률", own.redWinRate(), null));
	}

	/** 세트 기록이 있는 모든 팀의 지표. 쿼리 두 번으로 리그 전체를 계산한다. */
	private Map<Long, StatValues> leagueStats() {
		Map<Long, List<Long>> goldDiffs = goldDiffsAt15();
		Map<Long, StatValues> league = new HashMap<>();
		teamRepository.findAllTeamSetSummaries().stream()
				.collect(Collectors.groupingBy(TeamSetSummary::teamId))
				.forEach((teamId, sets) -> league.put(teamId, statValues(sets, goldDiffs.get(teamId))));
		return league;
	}

	private static StatValues statValues(List<TeamSetSummary> sets, List<Long> goldDiffs) {
		List<TeamSetSummary> blue = sets.stream().filter(s -> s.side() == Side.BLUE).toList();
		List<TeamSetSummary> red = sets.stream().filter(s -> s.side() == Side.RED).toList();
		int avgDurationSec = (int) Math.round(
				sets.stream().mapToInt(s -> s.playTime().toSecondOfDay()).average().orElseThrow());
		Double goldDiffAt15 = goldDiffs == null ? null
				: Decimals.stat(goldDiffs.stream().mapToLong(Long::longValue).average().orElseThrow());
		return new StatValues(
				goldDiffAt15,
				rate(sets, TeamSetSummary::firstDragon),
				rate(sets, TeamSetSummary::firstTower),
				rate(sets, TeamSetSummary::firstBaron),
				avgDurationSec,
				rate(blue, TeamSetSummary::victory),
				rate(red, TeamSetSummary::victory));
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

	/** 1 + 이 값보다 큰 값을 가진 팀 수. 값이 없으면 null. */
	private static <T extends Number & Comparable<T>> Integer rankOf(T own, Map<Long, StatValues> league,
			Function<StatValues, T> metric) {
		if (own == null) {
			return null;
		}
		long better = league.values().stream()
				.map(metric)
				.filter(Objects::nonNull)
				.filter(value -> value.compareTo(own) > 0)
				.count();
		return (int) better + 1;
	}

	private List<RecentMatch> recentMatches(Long teamId) {
		return finishedMatches().stream()
				.filter(match -> match.involves(teamId))
				.sorted(Comparator.comparing((FinishedMatch match) -> match.home().matchTime()).reversed())
				.limit(RECENT_COUNT)
				.map(match -> {
					MatchTeamResult own = match.side(teamId);
					MatchTeamResult opponent = match.other(teamId);
					return new RecentMatch(own.matchId(), own.matchTime().toLocalDate(),
							new Opponent(opponent.teamId(), opponent.teamCode()), own.won(),
							own.setWins() + ":" + opponent.setWins());
				})
				.toList();
	}

	/** 끝난 정규 시즌 경기, 오래된 순. */
	private List<FinishedMatch> regularMatches() {
		return finishedMatches().stream()
				.filter(match -> match.home().regular())
				.sorted(Comparator.comparing(match -> match.home().matchTime()))
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

	/** 비율 0~1, 소수 3자리. 대상이 없으면 null. */
	private static <T> Double rate(List<T> rows, Function<T, Boolean> flag) {
		if (rows.isEmpty()) {
			return null;
		}
		long count = rows.stream().map(flag).filter(Predicate.isEqual(true)).count();
		return Decimals.rate(count, rows.size());
	}

	/** 팀 지표 원값 (순위 계산 전). */
	private record StatValues(
			Double goldDiffAt15,
			Double firstDragonRate,
			Double firstTowerRate,
			Double firstBaronRate,
			Integer avgDurationSec,
			Double blueWinRate,
			Double redWinRate) {

		static final StatValues EMPTY = new StatValues(null, null, null, null, null, null, null);
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
			if (won) {
				wins++;
			} else {
				losses++;
			}
			setWins += result.setWins().intValue();
			setLosses += (int) (result.setsPlayed() - result.setWins());
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
			double winRate = matches == 0 ? 0.0 : Decimals.rate(wins, matches);
			List<String> recent5 = new ArrayList<>(results.subList(Math.max(0, results.size() - RECENT_COUNT), results.size()));
			Collections.reverse(recent5);
			return new StandingResponse(rank, teamId, code, matches, wins, losses, setWins, setLosses,
					setDiff(), winRate, List.copyOf(recent5));
		}
	}
}
