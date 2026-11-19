package com.riftdata.domain.match.service;

import com.riftdata.domain.match.dto.MatchStatus;
import com.riftdata.domain.match.dto.response.MatchCalendarResponse;
import com.riftdata.domain.match.dto.response.MatchSummaryResponse;
import com.riftdata.domain.match.dto.response.StageResponse;
import com.riftdata.domain.match.dto.response.TeamScoreResponse;
import com.riftdata.domain.match.entity.LckMatch;
import com.riftdata.domain.match.repository.LckMatchRepository;
import com.riftdata.domain.record.dto.SetPog;
import com.riftdata.domain.record.dto.SetWinner;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.record.repository.TeamSetRepository;
import com.riftdata.global.common.KstTime;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
	private final TeamSetRepository teamSetRepository;
	private final PlayerSetRepository playerSetRepository;
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

	private MatchSummaryResponse toSummary(LckMatch match, List<SetWinner> winners, List<SetPog> pogs) {
		MatchStatus status = MatchStatus.of(winners.size());
		boolean finished = status == MatchStatus.FINISHED;

		Map<Long, Long> winsByTeam = winners.stream()
				.collect(Collectors.groupingBy(SetWinner::winnerTeamId, Collectors.counting()));
		Integer scoreA = finished ? winsByTeam.getOrDefault(match.getTeam1().getId(), 0L).intValue() : null;
		Integer scoreB = finished ? winsByTeam.getOrDefault(match.getTeam2().getId(), 0L).intValue() : null;

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
				TeamScoreResponse.of(match.getTeam1(), scoreA),
				TeamScoreResponse.of(match.getTeam2(), scoreB),
				sets,
				seriesPog(pogs));
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
