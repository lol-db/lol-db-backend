package com.riftdata.domain.team.service;

import com.riftdata.domain.team.dto.StandingResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse.RecentMatch;
import com.riftdata.domain.team.dto.TeamDetailResponse.RosterPlayer;
import com.riftdata.domain.team.dto.TeamDetailResponse.TeamStats;
import com.riftdata.domain.team.dto.TeamResponse;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.MatchTeamResult;
import com.riftdata.domain.team.repository.RosterRow;
import com.riftdata.domain.team.repository.SetGoldAt15;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.domain.team.repository.TeamSetSummary;
import com.riftdata.global.common.Side;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	 * 정렬: 경기 승수 → 세트 득실 → 세트 승수 → 팀코드. 승자승은 아직 반영하지 않는다.
	 */
	public List<StandingResponse> getStandings() {
		Map<Long, StandingTally> tallies = new LinkedHashMap<>();
		for (Team team : teamRepository.findAllByOrderByCodeAsc()) {
			tallies.put(team.getId(), new StandingTally(team.getId(), team.getCode()));
		}

		finishedMatches().stream()
				.filter(match -> match.home().regular())
				.sorted(Comparator.comparing(match -> match.home().matchTime()))
				.forEach(match -> {
					tallies.get(match.home().teamId()).add(match.home());
					tallies.get(match.away().teamId()).add(match.away());
				});

		List<StandingTally> sorted = tallies.values().stream()
				.sorted(Comparator.comparingInt(StandingTally::wins).reversed()
						.thenComparing(Comparator.comparingInt(StandingTally::setDiff).reversed())
						.thenComparing(Comparator.comparingInt((StandingTally r) -> r.setWins).reversed())
						.thenComparing(r -> r.code))
				.toList();

		List<StandingResponse> standings = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			standings.add(sorted.get(i).toResponse(i + 1));
		}
		return standings;
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
		return teamRepository.findRoster(teamId).stream()
				.sorted(Comparator.comparing((RosterRow row) -> row.player().getPosition())
						.thenComparing(row -> row.player().getNickname()))
				.map(row -> new RosterPlayer(
						row.player().getId(),
						row.player().getNickname(),
						row.player().getName(),
						row.player().getPosition(),
						row.season() == null ? null : row.season().getSets(),
						row.season() == null ? null : row.season().getTotalPoints()))
				.toList();
	}

	private TeamStats stats(Long teamId) {
		List<TeamSetSummary> sets = teamRepository.findTeamSetSummaries(teamId);
		List<TeamSetSummary> blue = sets.stream().filter(s -> s.side() == Side.BLUE).toList();
		List<TeamSetSummary> red = sets.stream().filter(s -> s.side() == Side.RED).toList();

		Integer avgPlaySeconds = sets.isEmpty() ? null
				: (int) Math.round(sets.stream().mapToInt(s -> s.playTime().toSecondOfDay()).average().orElseThrow());

		return new TeamStats(
				sets.size(),
				goldDiffAt15(teamId),
				rate(sets, TeamSetSummary::firstDragon),
				rate(sets, TeamSetSummary::firstTower),
				rate(sets, TeamSetSummary::firstBaron),
				avgPlaySeconds,
				rate(blue, TeamSetSummary::victory),
				rate(red, TeamSetSummary::victory));
	}

	/** 양 팀 5명 모두 15분 골드가 있는 세트만 평균한다. 그런 세트가 없으면 null. */
	private Integer goldDiffAt15(Long teamId) {
		Map<Long, List<SetGoldAt15>> bySet = teamRepository.findGoldAt15InTeamSets(teamId).stream()
				.collect(Collectors.groupingBy(SetGoldAt15::setId));

		List<Long> diffs = new ArrayList<>();
		for (List<SetGoldAt15> rows : bySet.values()) {
			if (rows.size() != 2 || rows.stream().anyMatch(r -> r.playersWithData() != 5)) {
				continue;
			}
			SetGoldAt15 own = rows.get(0).teamId().equals(teamId) ? rows.get(0) : rows.get(1);
			SetGoldAt15 opponent = own == rows.get(0) ? rows.get(1) : rows.get(0);
			diffs.add(own.goldAt15() - opponent.goldAt15());
		}
		return diffs.isEmpty() ? null
				: (int) Math.round(diffs.stream().mapToLong(Long::longValue).average().orElseThrow());
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
