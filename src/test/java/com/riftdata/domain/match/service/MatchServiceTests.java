package com.riftdata.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riftdata.domain.match.dto.response.MatchDetailResponse;
import com.riftdata.domain.match.dto.response.MatchSummaryResponse;
import com.riftdata.domain.match.repository.LckMatchRepository;
import com.riftdata.domain.match.repository.MatchSetRepository;
import com.riftdata.domain.record.dto.PlayerSetPoints;
import com.riftdata.domain.record.dto.SetPog;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.record.repository.TeamSetRepository;
import com.riftdata.domain.team.service.TeamStandingReader;
import com.riftdata.global.common.KstTime;
import com.riftdata.global.common.Position;
import com.riftdata.support.MatchFixture;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MatchServiceTests {

	@Autowired EntityManager em;
	@Autowired LckMatchRepository lckMatchRepository;
	@Autowired MatchSetRepository matchSetRepository;
	@Autowired TeamSetRepository teamSetRepository;
	@Autowired PlayerSetRepository playerSetRepository;
	@Autowired ObjectProvider<TeamStandingReader> teamStandingReader;

	@Test
	@DisplayName("기간을 안 주면 오늘(Clock 기준)을 포함한 최근 7일을 준다")
	void defaultRangeIsLastSevenDaysUntilToday() {
		MatchFixture.Data data = MatchFixture.create(em);

		// 경기일 6일 뒤가 오늘이면 경기일은 기본 기간의 첫날이라 포함된다
		List<MatchSummaryResponse> included = serviceAt(MatchFixture.MATCH_DAY.plusDays(6))
				.getMatches(null, null, null, data.teamA().getId(), null);
		// 7일 뒤가 오늘이면 기간 밖이다
		List<MatchSummaryResponse> excluded = serviceAt(MatchFixture.MATCH_DAY.plusDays(7))
				.getMatches(null, null, null, data.teamA().getId(), null);

		assertThat(included).extracting(MatchSummaryResponse::matchId)
				.containsExactly(data.finished().getId(), data.scheduled().getId());
		assertThat(excluded).isEmpty();
	}

	@Test
	@DisplayName("시리즈 POG는 세트 POG를 가장 많이 받은 선수다")
	void seriesPogIsMostFrequent() {
		List<SetPog> pogs = List.of(pog(1, 10L, "A"), pog(2, 20L, "B"), pog(3, 10L, "A"));

		assertThat(MatchService.seriesPog(pogs)).isEqualTo(new MatchSummaryResponse.Pog(10L, "A"));
	}

	@Test
	@DisplayName("POG 횟수가 같으면 더 나중 세트의 POG 선수다")
	void seriesPogTieGoesToLaterSet() {
		List<SetPog> pogs = List.of(pog(1, 10L, "A"), pog(2, 20L, "B"));

		assertThat(MatchService.seriesPog(pogs)).isEqualTo(new MatchSummaryResponse.Pog(20L, "B"));
	}

	@Test
	@DisplayName("세트 POG가 없으면 시리즈 POG도 null이다")
	void seriesPogIsNullWithoutSetPogs() {
		assertThat(MatchService.seriesPog(List.of())).isNull();
	}

	@Test
	@DisplayName("시리즈 평점은 포지션 순이고, 안 나온 세트는 null이며 평균에서 빠진다")
	void playerRatingsWithSubstitute() {
		// 1·2세트는 MID_A, 3세트는 교체 선수 MID_B. TOP은 3세트 평점이 계산 전(null)
		List<PlayerSetPoints> records = List.of(
				points(1, 7L, "MID_A", Position.MID, 88),
				points(1, 5L, "TOP", Position.TOP, 50),
				points(2, 7L, "MID_A", Position.MID, 50),
				points(2, 5L, "TOP", Position.TOP, 75),
				points(3, 8L, "MID_B", Position.MID, 95),
				points(3, 5L, "TOP", Position.TOP, null),
				new PlayerSetPoints(1, 99L, 1L, "OTHER_TEAM", Position.TOP, 100));

		MatchDetailResponse.TeamPlayerRatings result =
				MatchService.teamPlayerRatings(10L, records, List.of(1, 2, 3));

		assertThat(result.players()).extracting(MatchDetailResponse.PlayerRating::name)
				.containsExactly("TOP", "MID_A", "MID_B");
		assertThat(result.players().get(0).setRatings()).isEqualTo(Arrays.asList(6.0, 8.0, null));
		assertThat(result.players().get(0).avgRating()).isEqualTo(7.0);
		assertThat(result.players().get(1).setRatings()).isEqualTo(Arrays.asList(9.0, 6.0, null));
		assertThat(result.players().get(1).avgRating()).isEqualTo(7.5);
		assertThat(result.players().get(2).setRatings()).isEqualTo(Arrays.asList(null, null, 9.6));
	}

	private static PlayerSetPoints points(int setNumber, Long playerId, String name, Position position,
			Integer totalPoints) {
		return new PlayerSetPoints(setNumber, 10L, playerId, name, position, totalPoints);
	}

	private MatchService serviceAt(LocalDate today) {
		Clock clock = Clock.fixed(today.atTime(LocalTime.NOON).atZone(KstTime.ZONE).toInstant(), KstTime.ZONE);
		return new MatchService(lckMatchRepository, matchSetRepository, teamSetRepository, playerSetRepository,
				teamStandingReader, clock);
	}

	private static SetPog pog(int setNumber, Long playerId, String name) {
		return new SetPog(1L, setNumber, 1L, playerId, name, 1L, "champ", 80);
	}
}
