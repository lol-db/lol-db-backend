package com.riftdata.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riftdata.domain.match.dto.response.MatchSummaryResponse;
import com.riftdata.domain.match.repository.LckMatchRepository;
import com.riftdata.domain.record.dto.SetPog;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.record.repository.TeamSetRepository;
import com.riftdata.global.common.KstTime;
import com.riftdata.support.MatchFixture;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MatchServiceTests {

	@Autowired EntityManager em;
	@Autowired LckMatchRepository lckMatchRepository;
	@Autowired TeamSetRepository teamSetRepository;
	@Autowired PlayerSetRepository playerSetRepository;

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

	private MatchService serviceAt(LocalDate today) {
		Clock clock = Clock.fixed(today.atTime(LocalTime.NOON).atZone(KstTime.ZONE).toInstant(), KstTime.ZONE);
		return new MatchService(lckMatchRepository, teamSetRepository, playerSetRepository, clock);
	}

	private static SetPog pog(int setNumber, Long playerId, String name) {
		return new SetPog(1L, setNumber, 1L, playerId, name, 1L, "champ", 80);
	}
}
