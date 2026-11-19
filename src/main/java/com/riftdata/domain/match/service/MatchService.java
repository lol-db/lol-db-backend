package com.riftdata.domain.match.service;

import com.riftdata.domain.match.dto.MatchStatus;
import com.riftdata.domain.match.dto.response.ChampionResponse;
import com.riftdata.domain.match.dto.response.MatchCalendarResponse;
import com.riftdata.domain.match.dto.response.MatchDetailResponse;
import com.riftdata.domain.match.dto.response.MatchSummaryResponse;
import com.riftdata.domain.match.dto.response.StageResponse;
import com.riftdata.domain.match.dto.response.TeamScoreResponse;
import com.riftdata.domain.match.entity.LckMatch;
import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.match.repository.LckMatchRepository;
import com.riftdata.domain.match.repository.MatchSetRepository;
import com.riftdata.domain.record.dto.PlayerSetPoints;
import com.riftdata.domain.record.dto.SetPog;
import com.riftdata.domain.record.dto.SetWinner;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.record.repository.TeamSetRepository;
import com.riftdata.domain.team.dto.TeamStanding;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.service.TeamStandingReader;
import com.riftdata.global.common.KstTime;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {

	/** 기간을 안 주면 오늘을 포함한 최근 7일 (오늘 − 6일 ~ 오늘) */
	private static final int DEFAULT_LIST_DAYS = 7;

	/** 최근 날짜 먼저, 같은 날은 시작 시각 순 */
	private static final Comparator<LckMatch> LIST_ORDER = Comparator
			.comparing((LckMatch m) -> m.getMatchTime().toLocalDate()).reversed()
			.thenComparing(LckMatch::getMatchTime);

	private final LckMatchRepository lckMatchRepository;
	private final MatchSetRepository matchSetRepository;
	private final TeamSetRepository teamSetRepository;
	private final PlayerSetRepository playerSetRepository;
	/** 팀·순위 도메인이 구현한다. 구현이 없으면 경기 상세 조회 때 오류가 난다. */
	private final ObjectProvider<TeamStandingReader> teamStandingReader;
	private final Clock clock;

	/** 경기 목록 (API-04). from·to는 포함 범위이고, 없으면 최근 7일이다. */
	public List<MatchSummaryResponse> getMatches(
			LocalDate from, LocalDate to, Long stageId, Long teamId, MatchStatus status) {
		LocalDate end = to != null ? to : LocalDate.now(clock);
		LocalDate start = from != null ? from : end.minusDays(DEFAULT_LIST_DAYS - 1);
		if (start.isAfter(end)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		List<LckMatch> matches = lckMatchRepository.findForList(
				start.atStartOfDay(), end.plusDays(1).atStartOfDay(), stageId, teamId);
		if (matches.isEmpty()) {
			return List.of();
		}

		List<Long> matchIds = matches.stream().map(LckMatch::getId).toList();
		Map<Long, List<SetWinner>> winnersByMatch = teamSetRepository.findSetWinners(matchIds).stream()
				.collect(Collectors.groupingBy(SetWinner::matchId));
		Map<Long, List<SetPog>> pogsByMatch = playerSetRepository.findSetPogs(matchIds).stream()
				.collect(Collectors.groupingBy(SetPog::matchId));

		return matches.stream()
				.sorted(LIST_ORDER)
				.map(m -> toSummary(m,
						winnersByMatch.getOrDefault(m.getId(), List.of()),
						pogsByMatch.getOrDefault(m.getId(), List.of())))
				.filter(summary -> status == null || summary.status() == status)
				.toList();
	}

	/** 월별 경기 캘린더 (API-05). 경기가 있는 날만 날짜 순으로 준다. */
	public MatchCalendarResponse getCalendar(YearMonth month) {
		List<LocalDateTime> matchTimes = lckMatchRepository.findMatchTimes(
				month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());

		Map<LocalDate, Long> countByDate = matchTimes.stream()
				.collect(Collectors.groupingBy(LocalDateTime::toLocalDate, TreeMap::new, Collectors.counting()));
		List<MatchCalendarResponse.Day> days = countByDate.entrySet().stream()
				.map(e -> new MatchCalendarResponse.Day(e.getKey(), e.getValue()))
				.toList();
		return new MatchCalendarResponse(month.toString(), days);
	}

	/** 경기(시리즈) 상세 (API-06). 없는 경기면 404 MATCH_NOT_FOUND */
	public MatchDetailResponse getMatch(Long matchId) {
		LckMatch match = lckMatchRepository.findDetailById(matchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
		Team teamA = match.getTeam1();
		Team teamB = match.getTeam2();

		List<SetWinner> winners = teamSetRepository.findSetWinners(List.of(matchId));
		MatchStatus status = MatchStatus.of(winners.size());
		Map<Long, TeamStanding> standings = teamStandingReader.getObject()
				.findByTeamIds(List.of(teamA.getId(), teamB.getId()));

		List<MatchDetailResponse.SetSummary> sets = List.of();
		List<MatchDetailResponse.TeamPlayerRatings> playerRatings = List.of();
		List<MatchDetailResponse.SetPogResponse> pogs = List.of();
		if (status == MatchStatus.FINISHED) {
			Map<Integer, Long> winnerBySet = winners.stream()
					.collect(Collectors.toMap(SetWinner::setNumber, SetWinner::winnerTeamId));
			List<MatchSet> playedSets = matchSetRepository.findByMatchIdOrderBySetNumberAsc(matchId);
			sets = playedSets.stream()
					.map(s -> new MatchDetailResponse.SetSummary(
							s.getSetNumber(), winnerBySet.get(s.getSetNumber()), s.getPlaySeconds()))
					.toList();
			List<Integer> setNumbers = playedSets.stream().map(MatchSet::getSetNumber).toList();
			List<PlayerSetPoints> records = playerSetRepository.findPlayerSetPoints(matchId);
			playerRatings = List.of(
					teamPlayerRatings(teamA.getId(), records, setNumbers),
					teamPlayerRatings(teamB.getId(), records, setNumbers));
			pogs = playerSetRepository.findSetPogs(List.of(matchId)).stream()
					.map(p -> new MatchDetailResponse.SetPogResponse(
							p.setNumber(), p.playerId(), p.nickname(), p.teamId(),
							new ChampionResponse(p.championId(), p.championName()), p.rating()))
					.toList();
		}

		Map<Long, Long> winsByTeam = countWins(winners);
		return new MatchDetailResponse(
				match.getId(),
				StageResponse.from(match.getStage()),
				KstTime.toOffset(match.getMatchTime()),
				status,
				match.getSets(),
				detailTeam(teamA, status, winsByTeam, standings.get(teamA.getId())),
				detailTeam(teamB, status, winsByTeam, standings.get(teamB.getId())),
				sets,
				playerRatings,
				pogs);
	}

	private MatchSummaryResponse toSummary(LckMatch match, List<SetWinner> winners, List<SetPog> pogs) {
		MatchStatus status = MatchStatus.of(winners.size());
		Map<Long, Long> winsByTeam = countWins(winners);

		List<MatchSummaryResponse.SetResult> sets = winners.stream()
				.map(w -> new MatchSummaryResponse.SetResult(w.setNumber(), w.winnerTeamId()))
				.toList();

		return new MatchSummaryResponse(
				match.getId(),
				match.getMatchTime().toLocalDate(),
				KstTime.toOffset(match.getMatchTime()),
				StageResponse.from(match.getStage()),
				status,
				match.getSets(),
				TeamScoreResponse.of(match.getTeam1(), score(match.getTeam1(), status, winsByTeam)),
				TeamScoreResponse.of(match.getTeam2(), score(match.getTeam2(), status, winsByTeam)),
				sets,
				seriesPog(pogs));
	}

	private static Map<Long, Long> countWins(List<SetWinner> winners) {
		return winners.stream().collect(Collectors.groupingBy(SetWinner::winnerTeamId, Collectors.counting()));
	}

	/** 이긴 세트 수. 예정 경기면 null */
	private static Integer score(Team team, MatchStatus status, Map<Long, Long> winsByTeam) {
		return status == MatchStatus.FINISHED ? winsByTeam.getOrDefault(team.getId(), 0L).intValue() : null;
	}

	private static MatchDetailResponse.Team detailTeam(
			Team team, MatchStatus status, Map<Long, Long> winsByTeam, TeamStanding standing) {
		return new MatchDetailResponse.Team(
				team.getId(),
				team.getCode(),
				score(team, status, winsByTeam),
				standing == null ? null : standing.rank(),
				standing == null ? null : standing.wins(),
				standing == null ? null : standing.losses());
	}

	/**
	 * 한 팀 선수들의 시리즈 평점. 선수는 처음 나온 세트의 포지션 순(TOP → SUPPORT)이고,
	 * 같은 포지션에 교체 선수가 있으면 먼저 나온 선수가 앞이다.
	 */
	static MatchDetailResponse.TeamPlayerRatings teamPlayerRatings(
			Long teamId, List<PlayerSetPoints> records, List<Integer> setNumbers) {
		Map<Long, List<PlayerSetPoints>> byPlayer = new LinkedHashMap<>();
		records.stream()
				.filter(r -> teamId.equals(r.teamId()))
				.sorted(Comparator.comparing(PlayerSetPoints::setNumber))
				.forEach(r -> byPlayer.computeIfAbsent(r.playerId(), id -> new ArrayList<>()).add(r));

		List<MatchDetailResponse.PlayerRating> players = byPlayer.values().stream()
				.sorted(Comparator.comparing((List<PlayerSetPoints> rs) -> rs.getFirst().position())
						.thenComparing(rs -> rs.getFirst().setNumber()))
				.map(rs -> playerRating(rs, setNumbers))
				.toList();
		return new MatchDetailResponse.TeamPlayerRatings(teamId, players);
	}

	private static MatchDetailResponse.PlayerRating playerRating(
			List<PlayerSetPoints> records, List<Integer> setNumbers) {
		Map<Integer, Double> ratingBySet = new HashMap<>();
		records.forEach(r -> ratingBySet.put(r.setNumber(), r.rating()));
		Double[] setRatings = setNumbers.stream().map(ratingBySet::get).toArray(Double[]::new);

		PlayerSetPoints first = records.getFirst();
		return new MatchDetailResponse.PlayerRating(
				first.playerId(), first.nickname(), first.position(),
				Arrays.asList(setRatings), Rating.average(Arrays.asList(setRatings)));
	}

	/**
	 * 시리즈 POG. 세트 POG를 가장 많이 받은 선수이고, 횟수가 같으면 더 나중 세트에서 POG를 받은 선수다.
	 * 세트 POG가 하나도 없으면(예정 경기, POG 계산 전) null이다.
	 */
	static MatchSummaryResponse.Pog seriesPog(List<SetPog> pogs) {
		Map<Long, Integer> countByPlayer = new HashMap<>();
		Map<Long, Integer> lastSetByPlayer = new HashMap<>();
		Map<Long, SetPog> byPlayer = new LinkedHashMap<>();
		for (SetPog pog : pogs) {
			countByPlayer.merge(pog.playerId(), 1, Integer::sum);
			lastSetByPlayer.merge(pog.playerId(), pog.setNumber(), Math::max);
			byPlayer.putIfAbsent(pog.playerId(), pog);
		}

		return byPlayer.keySet().stream()
				.max(Comparator.comparing((Function<Long, Integer>) countByPlayer::get)
						.thenComparing(lastSetByPlayer::get))
				.map(byPlayer::get)
				.map(pog -> new MatchSummaryResponse.Pog(pog.playerId(), pog.nickname()))
				.orElse(null);
	}
}
