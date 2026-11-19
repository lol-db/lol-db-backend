package com.riftdata.domain.player.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.domain.champion.entity.Champion;
import com.riftdata.domain.match.entity.LckMatch;
import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.match.entity.Stage;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.record.entity.PlayerSet;
import com.riftdata.domain.record.entity.TeamSet;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 실제 DB 기록과 HTTP 응답으로 선수 경기 기록을 검증하며, 각 검사 후 데이터를 롤백합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerGamesControllerTests {
	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;

	private Team ownTeam;
	private Team otherTeam;
	private Player player;
	private Player opposingMid;
	private Player opposingTop;
	private Champion championA;
	private Champion championB;
	private Stage stage;
	private int gameSequence;

	@BeforeEach
	void setUp() {
		ownTeam = persist(Team.builder().code("PGA").build());
		otherTeam = persist(Team.builder().code("PGB").build());
		player = persist(Player.builder().team(ownTeam).nickname("PG_Player").position(Position.MID).build());
		opposingMid = persist(Player.builder().team(otherTeam).nickname("PG_OppMid").position(Position.MID).build());
		opposingTop = persist(Player.builder().team(otherTeam).nickname("PG_OppTop").position(Position.TOP).build());
		championA = persist(Champion.builder().name("PG_ChampionA").build());
		championB = persist(Champion.builder().name("PG_ChampionB").build());
		stage = persist(Stage.builder().name("PG Regular").stageOrder(95)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).regular(true).build());
	}

	@Test
	@DisplayName("경기별 세트를 번호순으로 묶고 동일 포지션 상대와의 골드 차를 반환합니다.")
	void groupsSetsAndMapsFields() throws Exception {
		LckMatch match = game("2026-05-03T17:00:00", true);
		set(match, 3, true, championA, "26.8", 1800, 80, 5000, 4500, true);
		set(match, 1, true, championB, "26.8", 1200, 50, 4300, 4500, true);
		set(match, 2, false, championA, "26.8", 2400, 95, 4500, 4500, true);

		request(games())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(3))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.content[0].matchId").value(match.getId()))
				.andExpect(jsonPath("$.content[0].date").value("2026-05-03"))
				.andExpect(jsonPath("$.content[0].stage.stageId").value(stage.getId()))
				.andExpect(jsonPath("$.content[0].stage.name").value("PG Regular"))
				.andExpect(jsonPath("$.content[0].stage.regular").value(true))
				.andExpect(jsonPath("$.content[0].round").doesNotExist())
				.andExpect(jsonPath("$.content[0].opponent.teamId").value(otherTeam.getId()))
				.andExpect(jsonPath("$.content[0].opponent.code").value("PGB"))
				.andExpect(jsonPath("$.content[0].opponent.name").doesNotExist())
				.andExpect(jsonPath("$.content[0].score").value("2:1"))
				.andExpect(jsonPath("$.content[0].win").value(true))
				.andExpect(jsonPath("$.content[0].sets.length()").value(3))
				.andExpect(jsonPath("$.content[0].sets[0].setNo").value(1))
				.andExpect(jsonPath("$.content[0].sets[2].setNo").value(3))
				.andExpect(jsonPath("$.content[0].sets[0].goldDiff15").value(-200))
				.andExpect(jsonPath("$.content[0].sets[2].goldDiff15").value(500))
				.andExpect(jsonPath("$.content[0].sets[2].champion.championId").value(championA.getId()))
				.andExpect(jsonPath("$.content[0].sets[2].champion.name").value("PG_ChampionA"))
				.andExpect(jsonPath("$.content[0].sets[2].kills").value(2))
				.andExpect(jsonPath("$.content[0].sets[2].deaths").value(1))
				.andExpect(jsonPath("$.content[0].sets[2].assists").value(3))
				.andExpect(jsonPath("$.content[0].sets[2].csPerMin").value(7.0))
				.andExpect(jsonPath("$.content[0].sets[2].dpm").value(500.0))
				.andExpect(jsonPath("$.content[0].sets[2].durationSec").value(1800))
				.andExpect(jsonPath("$.content[0].sets[2].rating").value(8.4))
				.andExpect(jsonPath("$.content[0].sets[2].pog").value(true));
	}

	@Test
	@DisplayName("패배 세트만 골라도 경기 전체의 점수와 승패는 바뀌지 않습니다.")
	void filtersSetResultsWithoutChangingSeriesScore() throws Exception {
		LckMatch match = game("2026-05-03T17:00:00", true);
		set(match, 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		set(match, 2, false, championA, "26.8", 1800, 50, 5000, 4500, true);
		// 선수가 출전하지 않은 세트도 경기 점수에는 포함합니다.
		set(match, 3, true, championB, "26.8", 1800, 50, 5000, 4500, false);

		request(games().param("result", "LOSS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].score").value("2:1"))
				.andExpect(jsonPath("$.content[0].win").value(true))
				.andExpect(jsonPath("$.content[0].sets.length()").value(1))
				.andExpect(jsonPath("$.content[0].sets[0].setNo").value(2))
				.andExpect(jsonPath("$.content[0].sets[0].win").value(false));
	}

	@Test
	@DisplayName("패치 챔피언 상대 팀과 세트 승패 필터를 모두 함께 적용합니다.")
	void combinesAllFilters() throws Exception {
		LckMatch match = game("2026-05-03T17:00:00", true);
		set(match, 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		set(match, 2, false, championA, "26.8", 1800, 50, 5000, 4500, true);
		set(match, 3, true, championB, "26.9", 1800, 50, 5000, 4500, true);

		request(games().param("patch", " 26.8 ").param("championId", championA.getId().toString())
				.param("opponentTeamId", otherTeam.getId().toString()).param("result", "WIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].sets.length()").value(1))
				.andExpect(jsonPath("$.content[0].sets[0].setNo").value(1))
				.andExpect(jsonPath("$.content[0].sets[0].goldDiff15").value(500))
				.andExpect(jsonPath("$.content[0].score").value("2:1"));
		request(games().param("opponentTeamId", ownTeam.getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	@DisplayName("경기 단위로 페이지를 나누며 최근 경기부터 정렬합니다.")
	void paginatesMatchGroupsRatherThanSets() throws Exception {
		LckMatch oldest = game("2026-01-02T17:00:00", true);
		for (int i = 1; i <= 3; i++) set(oldest, i, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		for (int i = 3; i <= 5; i++) {
			LckMatch next = game("2026-01-0" + i + "T17:00:00", true);
			set(next, 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		}
		request(games()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].date").value("2026-01-05"));
		request(games().param("page", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].matchId").value(oldest.getId()))
				.andExpect(jsonPath("$.content[0].sets.length()").value(3));
		request(games().param("page", "2147483647"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(4));
	}

	@Test
	@DisplayName("같은 시각의 경기는 경기 번호 내림차순으로 안정적으로 정렬합니다.")
	void ordersEqualMatchTimesByMatchId() throws Exception {
		LckMatch first = game("2026-05-03T17:00:00", true);
		LckMatch second = game("2026-05-03T17:00:00", true);
		set(first, 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		set(second, 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		request(games().param("size", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].matchId").value(second.getId()))
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	@DisplayName("이적 후에도 당시 팀을 기준으로 상대 팀과 시리즈 점수를 표시합니다.")
	void usesHistoricalTeamEvenWhenItWasTeamTwo() throws Exception {
		LckMatch match = game("2026-05-03T17:00:00", false);
		set(match, 1, false, championA, "26.8", 1800, 50, 5000, 4500, true);
		set(match, 2, false, championA, "26.8", 1800, 50, 5000, 4500, true);
		set(match, 3, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		player.changeTeam(persist(Team.builder().code("PGC").build()));

		request(games()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].opponent.teamId").value(otherTeam.getId()))
				.andExpect(jsonPath("$.content[0].score").value("1:2"))
				.andExpect(jsonPath("$.content[0].win").value(false));
	}

	@Test
	@DisplayName("2026년 경계는 포함하고 다른 연도는 제외하며 플레이오프도 조회합니다.")
	void restrictsSeasonAndIncludesPlayoffs() throws Exception {
		for (String date : new String[]{"2025-12-31T23:59:59", "2026-01-01T00:00:00",
				"2026-12-31T23:59:59", "2027-01-01T00:00:00"}) {
			set(game(date, true), 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		}
		stage = persist(Stage.builder().name("PG Playoff").stageOrder(94)
				.startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 8, 31)).regular(false).build());
		set(game("2026-08-03T17:00:00", true), 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);

		request(games()).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].date").value("2026-12-31"))
				.andExpect(jsonPath("$.content[1].stage.regular").value(false))
				.andExpect(jsonPath("$.content[2].date").value("2026-01-01"));
	}

	@Test
	@DisplayName("시간이나 15분 기록과 점수가 없으면 계산 불가능한 값은 null입니다.")
	void preservesMissingStatisticsAndRating() throws Exception {
		LckMatch match = game("2026-05-03T17:00:00", true);
		set(match, 1, true, championA, "26.8", 0, null, 5000, null, true);
		set(match, 2, false, championA, "26.8", 1800, 50, null, 4500, true);

		request(games()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].win").value(nullValue()))
				.andExpect(jsonPath("$.content[0].sets[0].durationSec").value(0))
				.andExpect(jsonPath("$.content[0].sets[0].csPerMin").value(nullValue()))
				.andExpect(jsonPath("$.content[0].sets[0].dpm").value(nullValue()))
				.andExpect(jsonPath("$.content[0].sets[0].rating").value(nullValue()))
				.andExpect(jsonPath("$.content[0].sets[0].goldDiff15").value(nullValue()))
				.andExpect(jsonPath("$.content[0].sets[1].goldDiff15").value(nullValue()));
	}

	@Test
	@DisplayName("선수 기록이나 필터에 맞는 기록이 없으면 빈 페이지를 반환합니다.")
	void returnsEmptyPagesForAbsentRecordsAndUnmatchedFilters() throws Exception {
		request(games()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalPages").value(0));
		set(game("2026-05-03T17:00:00", true), 1, true, championA, "26.8", 1800, 50, 5000, 4500, true);
		request(games().param("patch", "unknown")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		request(games().param("championId", Long.toString(Long.MAX_VALUE))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
		request(games().param("result", "LOSS")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"page,-1", "size,0", "size,101", "size,wrong", "championId,0",
			"opponentTeamId,-1", "result,win", "result,DRAW", "patch,1234567890123456"})
	@DisplayName("잘못된 필터와 페이지는 공통 400 오류를 반환합니다.")
	void rejectsInvalidParameters(String parameter, String value) throws Exception {
		request(games().param(parameter, value)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "wrong"})
	@DisplayName("잘못된 선수 번호는 공통 400 오류입니다.")
	void rejectsInvalidPlayerId(String id) throws Exception {
		request(get("/api/v1/players/" + id + "/games")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	@DisplayName("존재하지 않는 선수 번호는 선수 404 오류입니다.")
	void rejectsMissingPlayer() throws Exception {
		request(get("/api/v1/players/" + Long.MAX_VALUE + "/games"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_001"));
	}

	private MockHttpServletRequestBuilder games() {
		return get("/api/v1/players/" + player.getId() + "/games");
	}

	private ResultActions request(MockHttpServletRequestBuilder request) throws Exception {
		em.flush();
		return mockMvc.perform(request);
	}

	private LckMatch game(String date, boolean targetTeam1) {
		return persist(LckMatch.builder().matchTime(LocalDateTime.parse(date)).sets(3)
				.team1(targetTeam1 ? ownTeam : otherTeam).team2(targetTeam1 ? otherTeam : ownTeam)
				.stage(stage).build());
	}

	private void set(LckMatch match, int number, boolean won, Champion champion, String patch,
			int seconds, Integer points, Integer ownGold, Integer opponentGold, boolean played) {
		MatchSet set = persist(MatchSet.builder().match(match).setNumber(number)
				.gameId("PG-GAME-" + (++gameSequence)).version(patch)
				.playTime(LocalTime.ofSecondOfDay(seconds)).build());
		TeamSet own = persist(TeamSet.builder().matchSet(set).team(ownTeam).side(Side.BLUE).victory(won).build());
		TeamSet other = persist(TeamSet.builder().matchSet(set).team(otherTeam).side(Side.RED).victory(!won).build());
		if (played) {
			persist(PlayerSet.builder().teamSet(own).player(player).position(Position.MID).champion(champion)
					.kills(2).deaths(1).assists(3).minionKills(200).monsterKills(10)
					.damageToChampions(15000).goldAt15(ownGold).totalPoints(points).pog(won).build());
		}
		persist(PlayerSet.builder().teamSet(other).player(opposingMid).position(Position.MID).champion(championB)
				.goldAt15(opponentGold).build());
		persist(PlayerSet.builder().teamSet(other).player(opposingTop).position(Position.TOP).champion(championB)
				.goldAt15(9999).build());
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}
}
