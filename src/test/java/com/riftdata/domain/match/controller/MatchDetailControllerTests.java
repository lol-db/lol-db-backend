package com.riftdata.domain.match.controller;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.domain.team.dto.TeamStanding;
import com.riftdata.domain.team.service.TeamStandingReader;
import com.riftdata.global.exception.ErrorCode;
import com.riftdata.support.MatchFixture;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경기 상세 API (실제 MySQL, 끝나면 롤백).
 * 순위 계산은 팀·순위 도메인 담당이라, 테스트에서는 순위 데이터를 직접 넣어 준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MatchDetailControllerTests {

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;
	@MockitoBean TeamStandingReader teamStandingReader;

	MatchFixture.Data data;

	@BeforeEach
	void setUp() {
		data = MatchFixture.create(em);
		Long a = data.teamA().getId();
		Long b = data.teamB().getId();
		given(teamStandingReader.findByTeamIds(anyCollection())).willReturn(Map.of(
				a, new TeamStanding(a, 1, 6, 2),
				b, new TeamStanding(b, 3, 5, 3)));
	}

	@Test
	@DisplayName("종료 경기는 헤더·세트·시리즈 선수 평점·세트별 POG를 준다")
	void finishedMatchDetail() throws Exception {
		Long a = data.teamA().getId();
		Long b = data.teamB().getId();
		mockMvc.perform(get("/api/v1/matches/{matchId}", data.finished().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.matchId").value(data.finished().getId()))
				.andExpect(jsonPath("$.stage.name").value("ZZ Regular"))
				.andExpect(jsonPath("$.startAt").value("2026-04-26T17:00:00+09:00"))
				.andExpect(jsonPath("$.status").value("FINISHED"))
				.andExpect(jsonPath("$.bestOf").value(3))
				// 헤더: 스코어 + 현재 순위
				.andExpect(jsonPath("$.teamA.code").value("ZZA"))
				.andExpect(jsonPath("$.teamA.score").value(2))
				.andExpect(jsonPath("$.teamA.rank").value(1))
				.andExpect(jsonPath("$.teamA.wins").value(6))
				.andExpect(jsonPath("$.teamA.losses").value(2))
				.andExpect(jsonPath("$.teamB.score").value(1))
				.andExpect(jsonPath("$.teamB.rank").value(3))
				// 세트: 30분 = 1800초
				.andExpect(jsonPath("$.sets.length()").value(3))
				.andExpect(jsonPath("$.sets[1].setNo").value(2))
				.andExpect(jsonPath("$.sets[1].winnerTeamId").value(b))
				.andExpect(jsonPath("$.sets[1].durationSec").value(1800))
				// 시리즈 평점: 팀 A 먼저, 포지션 순. ZZA_MID는 88·50·95점 → 9.0·6.0·9.6, 평균 8.2
				.andExpect(jsonPath("$.playerRatings[0].teamId").value(a))
				.andExpect(jsonPath("$.playerRatings[0].players.length()").value(5))
				.andExpect(jsonPath("$.playerRatings[0].players[0].position").value("TOP"))
				.andExpect(jsonPath("$.playerRatings[0].players[2].name").value("ZZA_MID"))
				.andExpect(jsonPath("$.playerRatings[0].players[2].setRatings[0]").value(9.0))
				.andExpect(jsonPath("$.playerRatings[0].players[2].setRatings[1]").value(6.0))
				.andExpect(jsonPath("$.playerRatings[0].players[2].setRatings[2]").value(9.6))
				.andExpect(jsonPath("$.playerRatings[0].players[2].avgRating").value(8.2))
				.andExpect(jsonPath("$.playerRatings[1].teamId").value(b))
				.andExpect(jsonPath("$.playerRatings[1].players[2].avgRating").value(6.8))
				// 세트별 POG
				.andExpect(jsonPath("$.pogs.length()").value(3))
				.andExpect(jsonPath("$.pogs[1].setNo").value(2))
				.andExpect(jsonPath("$.pogs[1].name").value("ZZB_MID"))
				.andExpect(jsonPath("$.pogs[1].teamId").value(b))
				.andExpect(jsonPath("$.pogs[1].champion.name").value("ZZChamp7"))
				.andExpect(jsonPath("$.pogs[1].rating").value(8.4));
	}

	@Test
	@DisplayName("예정 경기는 스코어가 null이고 세트·평점·POG는 빈 목록이다")
	void scheduledMatchDetail() throws Exception {
		mockMvc.perform(get("/api/v1/matches/{matchId}", data.scheduled().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.teamA.code").value("ZZB"))
				.andExpect(jsonPath("$.teamA.score").isEmpty())
				.andExpect(jsonPath("$.teamA.rank").value(3))
				.andExpect(jsonPath("$.sets.length()").value(0))
				.andExpect(jsonPath("$.playerRatings.length()").value(0))
				.andExpect(jsonPath("$.pogs.length()").value(0));
	}

	@Test
	@DisplayName("순위표에 없는 팀은 순위·승·패가 null이다")
	void missingStandingIsNull() throws Exception {
		given(teamStandingReader.findByTeamIds(anyCollection())).willReturn(Map.of());

		mockMvc.perform(get("/api/v1/matches/{matchId}", data.finished().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teamA.score").value(2))
				.andExpect(jsonPath("$.teamA.rank").isEmpty())
				.andExpect(jsonPath("$.teamA.wins").isEmpty())
				.andExpect(jsonPath("$.teamA.losses").isEmpty());
	}

	@Test
	@DisplayName("없는 경기는 404 MATCH_NOT_FOUND이다")
	void unknownMatchIsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/matches/{matchId}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.MATCH_NOT_FOUND.getCode()));
	}
}
