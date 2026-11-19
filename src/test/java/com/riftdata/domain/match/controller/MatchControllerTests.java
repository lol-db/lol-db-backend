package com.riftdata.domain.match.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.domain.match.entity.LckMatch;
import com.riftdata.domain.match.entity.Stage;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.global.exception.ErrorCode;
import com.riftdata.support.MatchFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경기 목록·캘린더 API (실제 MySQL, 끝나면 롤백).
 * 같은 날짜에 CSV 실데이터가 있을 수 있어서 목록은 teamId로 테스트 팀만 거른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MatchControllerTests {

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;

	MatchFixture.Data data;

	@BeforeEach
	void setUp() {
		data = MatchFixture.create(em);
	}

	@Test
	@DisplayName("같은 날 경기는 시작 시각 순이고, 종료 경기는 스코어·세트·시리즈 POG를 준다")
	void listsMatchesOfTheDay() throws Exception {
		String day = MatchFixture.MATCH_DAY.toString();
		mockMvc.perform(get("/api/v1/matches")
						.param("from", day).param("to", day).param("teamId", data.teamA().getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				// 17:00 종료 경기: ZZA 2 : 1 ZZB, ZZA_MID가 1·3세트 POG
				.andExpect(jsonPath("$[0].matchId").value(data.finished().getId()))
				.andExpect(jsonPath("$[0].date").value(day))
				.andExpect(jsonPath("$[0].startAt").value("2026-04-26T17:00:00+09:00"))
				.andExpect(jsonPath("$[0].stage.name").value("ZZ Regular"))
				.andExpect(jsonPath("$[0].status").value("FINISHED"))
				.andExpect(jsonPath("$[0].bestOf").value(3))
				.andExpect(jsonPath("$[0].teamA.code").value("ZZA"))
				.andExpect(jsonPath("$[0].teamA.score").value(2))
				.andExpect(jsonPath("$[0].teamB.code").value("ZZB"))
				.andExpect(jsonPath("$[0].teamB.score").value(1))
				.andExpect(jsonPath("$[0].sets.length()").value(3))
				.andExpect(jsonPath("$[0].sets[1].setNo").value(2))
				.andExpect(jsonPath("$[0].sets[1].winnerTeamId").value(data.teamB().getId()))
				.andExpect(jsonPath("$[0].pog.name").value("ZZA_MID"))
				// 20:00 예정 경기: 스코어 null, 세트 [], POG null
				.andExpect(jsonPath("$[1].matchId").value(data.scheduled().getId()))
				.andExpect(jsonPath("$[1].status").value("SCHEDULED"))
				.andExpect(jsonPath("$[1].teamA.score").isEmpty())
				.andExpect(jsonPath("$[1].sets.length()").value(0))
				.andExpect(jsonPath("$[1].pog").isEmpty());
	}

	@Test
	@DisplayName("최근 날짜가 먼저 나온다")
	void newerDateComesFirst() throws Exception {
		persistMatch(MatchFixture.MATCH_DAY.minusDays(1));

		mockMvc.perform(get("/api/v1/matches")
						.param("from", MatchFixture.MATCH_DAY.minusDays(1).toString())
						.param("to", MatchFixture.MATCH_DAY.toString())
						.param("teamId", data.teamA().getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].date").value("2026-04-26"))
				.andExpect(jsonPath("$[1].date").value("2026-04-26"))
				.andExpect(jsonPath("$[2].date").value("2026-04-25"));
	}

	@Test
	@DisplayName("상태로 거르면 그 상태의 경기만 나온다")
	void filtersByStatus() throws Exception {
		String day = MatchFixture.MATCH_DAY.toString();
		mockMvc.perform(get("/api/v1/matches")
						.param("from", day).param("to", day)
						.param("teamId", data.teamA().getId().toString())
						.param("status", "SCHEDULED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].matchId").value(data.scheduled().getId()));
	}

	@Test
	@DisplayName("다른 스테이지로 거르면 테스트 경기는 나오지 않는다")
	void filtersByStage() throws Exception {
		String day = MatchFixture.MATCH_DAY.toString();
		mockMvc.perform(get("/api/v1/matches")
						.param("from", day).param("to", day)
						.param("teamId", data.teamA().getId().toString())
						.param("stageId", String.valueOf(data.stage().getId() + 100_000)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("시작일이 종료일보다 늦으면 입력 오류 400이다")
	void rejectsReversedRange() throws Exception {
		mockMvc.perform(get("/api/v1/matches").param("from", "2026-04-27").param("to", "2026-04-26"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()));
	}

	@Test
	@DisplayName("없는 상태 값은 입력 오류 400이다")
	void rejectsUnknownStatus() throws Exception {
		mockMvc.perform(get("/api/v1/matches").param("status", "LIVE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()));
	}

	@Test
	@DisplayName("캘린더는 경기 있는 날만 날짜 순으로 경기 수를 준다")
	void calendarCountsMatchesPerDay() throws Exception {
		// 실데이터와 겹치지 않는 달에 경기를 넣는다
		persistMatch(LocalDate.of(2099, 1, 20));
		persistMatch(LocalDate.of(2099, 1, 3));
		persistMatch(LocalDate.of(2099, 1, 20));
		persistMatch(LocalDate.of(2099, 2, 1)); // 다음 달이라 빠진다

		mockMvc.perform(get("/api/v1/matches/calendar").param("month", "2099-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value("2099-01"))
				.andExpect(jsonPath("$.days.length()").value(2))
				.andExpect(jsonPath("$.days[0].date").value("2099-01-03"))
				.andExpect(jsonPath("$.days[0].matchCount").value(1))
				.andExpect(jsonPath("$.days[1].date").value("2099-01-20"))
				.andExpect(jsonPath("$.days[1].matchCount").value(2));
	}

	@Test
	@DisplayName("캘린더 month가 없거나 형식이 틀리면 입력 오류 400이다")
	void calendarRejectsBadMonth() throws Exception {
		mockMvc.perform(get("/api/v1/matches/calendar"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()));
		mockMvc.perform(get("/api/v1/matches/calendar").param("month", "2099-13"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()));
	}

	/** 테스트 팀끼리의 예정 경기 하나 (18:00) */
	private void persistMatch(LocalDate date) {
		em.persist(LckMatch.builder()
				.matchTime(date.atTime(18, 0)).sets(3)
				.team1(em.getReference(Team.class, data.teamA().getId()))
				.team2(em.getReference(Team.class, data.teamB().getId()))
				.stage(em.getReference(Stage.class, data.stage().getId()))
				.build());
		em.flush();
	}
}
