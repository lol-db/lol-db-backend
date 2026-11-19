package com.riftdata.domain.match.service;

import com.riftdata.domain.match.dto.response.ChampionResponse;
import com.riftdata.domain.match.dto.response.HomeResponse;
import com.riftdata.domain.match.dto.response.MatchSummaryResponse;
import com.riftdata.domain.player.dto.PositionLeader;
import com.riftdata.domain.player.service.PlayerSeasonReader;
import com.riftdata.domain.record.dto.RatedSet;
import com.riftdata.domain.record.dto.RecentPog;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.team.dto.TeamStanding;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.domain.team.service.TeamStandingReader;
import com.riftdata.global.common.Rating;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 (API-03). 경기 데이터는 직접 읽고, 순위는 팀·순위 도메인, 시즌 등급·포지션 리더는 선수 도메인에서 가져온다.
 * 두 도메인의 구현이 없으면 홈 조회 때 오류가 난다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

	/** 최근 평점 상위: 기준일 포함 최근 7일, 3세트 이상, 5명 */
	static final int TOP_RATED_DAYS = 7;
	static final int TOP_RATED_MIN_SETS = 3;
	static final int TOP_RATED_LIMIT = 5;
	/** 순위 미니표: 상위 6팀 (플레이오프 진출 수) */
	static final int STANDINGS_LIMIT = 6;
	/** 최근 POG: 기준일 포함 최근 3일, 평점 순 3명 */
	static final int RECENT_POG_DAYS = 3;
	static final int RECENT_POG_LIMIT = 3;

	private final MatchService matchService;
	private final PlayerSetRepository playerSetRepository;
	private final TeamRepository teamRepository;
	private final ObjectProvider<TeamStandingReader> teamStandingReader;
	private final ObjectProvider<PlayerSeasonReader> playerSeasonReader;
	private final Clock clock;

	/** date가 없으면 오늘(Clock 기준) */
	public HomeResponse getHome(LocalDate date) {
		LocalDate day = date != null ? date : LocalDate.now(clock);

		List<MatchSummaryResponse> todayMatches = matchService.getMatches(day, day, null, null, null);

		LocalDate topRatedFrom = day.minusDays(TOP_RATED_DAYS - 1);
		List<RatedSet> ratedSets = playerSetRepository.findRatedSets(
				topRatedFrom.atStartOfDay(), day.plusDays(1).atStartOfDay());
		List<HomeResponse.TopRatedPlayer> topRated = topRatedPlayers(ratedSets);
		HomeResponse.TopRatedPlayers topRatedPlayers = new HomeResponse.TopRatedPlayers(
				topRatedFrom, day, TOP_RATED_MIN_SETS, withSeasonGrades(topRated));

		List<RecentPog> pogs = playerSetRepository.findRecentPogs(
				day.minusDays(RECENT_POG_DAYS - 1).atStartOfDay(), day.plusDays(1).atStartOfDay());

		return new HomeResponse(
				day,
				todayMatches,
				topRatedPlayers,
				standingsTop(),
				positionLeaders(),
				recentPogs(pogs));
	}

	/**
	 * 기간 안 세트 기록으로 평균 평점 상위 선수를 고른다. 시즌 등급은 아직 비어 있다(null).
	 * 정렬: 평균 평점 → 세트 수 → 선수 ID. mainChampion은 가장 많이 쓴 챔피언이고, 같으면 더 최근에 쓴 챔피언이다.
	 */
	static List<HomeResponse.TopRatedPlayer> topRatedPlayers(List<RatedSet> ratedSets) {
		Map<Long, List<RatedSet>> byPlayer = ratedSets.stream()
				.collect(Collectors.groupingBy(RatedSet::playerId, LinkedHashMap::new, Collectors.toList()));

		record Candidate(List<RatedSet> sets, Double avgRating) {
		}
		List<Candidate> candidates = byPlayer.values().stream()
				.filter(sets -> sets.size() >= TOP_RATED_MIN_SETS)
				.map(sets -> new Candidate(sets, Rating.average(sets.stream().map(RatedSet::rating).toList())))
				.sorted(Comparator.comparing(Candidate::avgRating).reversed()
						.thenComparing(c -> c.sets().size(), Comparator.reverseOrder())
						.thenComparing(c -> c.sets().getFirst().playerId()))
				.limit(TOP_RATED_LIMIT)
				.toList();

		List<HomeResponse.TopRatedPlayer> items = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			List<RatedSet> sets = candidates.get(i).sets();
			RatedSet first = sets.getFirst();
			items.add(new HomeResponse.TopRatedPlayer(
					i + 1, first.playerId(), first.nickname(), first.teamCode(), first.position(),
					sets.size(), mainChampion(sets), candidates.get(i).avgRating(), null));
		}
		return items;
	}

	private static ChampionResponse mainChampion(List<RatedSet> sets) {
		Map<Long, List<RatedSet>> byChampion = sets.stream().collect(Collectors.groupingBy(RatedSet::championId));
		RatedSet pick = byChampion.values().stream()
				.max(Comparator.comparing((List<RatedSet> rs) -> rs.size())
						.thenComparing(rs -> rs.stream().map(RatedSet::matchTime).max(Comparator.naturalOrder())
								.orElseThrow()))
				.orElseThrow()
				.getFirst();
		return new ChampionResponse(pick.championId(), pick.championName());
	}

	private List<HomeResponse.TopRatedPlayer> withSeasonGrades(List<HomeResponse.TopRatedPlayer> items) {
		if (items.isEmpty()) {
			return items;
		}
		Map<Long, String> grades = playerSeasonReader.getObject()
				.findSeasonGrades(items.stream().map(HomeResponse.TopRatedPlayer::playerId).toList());
		return items.stream()
				.map(p -> new HomeResponse.TopRatedPlayer(p.rank(), p.playerId(), p.name(), p.teamCode(),
						p.position(), p.sets(), p.mainChampion(), p.avgRating(), grades.get(p.playerId())))
				.toList();
	}

	private List<HomeResponse.StandingRow> standingsTop() {
		List<TeamStanding> standings = teamStandingReader.getObject().findTop(STANDINGS_LIMIT);
		Map<Long, String> codeById = teamRepository.findAllById(standings.stream().map(TeamStanding::teamId).toList())
				.stream()
				.collect(Collectors.toMap(Team::getId, Team::getCode));
		return standings.stream()
				.map(s -> new HomeResponse.StandingRow(
						s.rank(), s.teamId(), codeById.get(s.teamId()), s.wins(), s.losses(), s.setDiff()))
				.toList();
	}

	private List<PositionLeader> positionLeaders() {
		return playerSeasonReader.getObject().findPositionLeaders();
	}

	/** 평점 높은 순, 같으면 최근 경기 먼저 */
	static List<HomeResponse.RecentPogResponse> recentPogs(List<RecentPog> pogs) {
		Function<RecentPog, Double> rating = p -> p.rating() == null ? -1 : p.rating();
		return pogs.stream()
				.sorted(Comparator.comparing(rating).reversed()
						.thenComparing(RecentPog::matchTime, Comparator.reverseOrder())
						.thenComparing(RecentPog::setNumber, Comparator.reverseOrder()))
				.limit(RECENT_POG_LIMIT)
				.map(p -> new HomeResponse.RecentPogResponse(
						p.matchTime().toLocalDate(), p.matchId(), p.setNumber(), p.playerId(), p.nickname(),
						p.teamCode(), new ChampionResponse(p.championId(), p.championName()), p.rating()))
				.toList();
	}
}
