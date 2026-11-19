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
import com.riftdata.domain.player.entity.PlayerSeason;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** API-12의 DB 집계, 필터, 순위와 HTTP 응답을 검증하며 데이터는 롤백됩니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerRankingControllerTests {
	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;
	private Team team;
	private Team opponent;
	private Champion champion;
	private Stage stage;
	private int gameNumber;

	@BeforeEach
	void setUp() {
		team = persist(Team.builder().code("PRR").build());
		opponent = persist(Team.builder().code("PRO").build());
		champion = persist(Champion.builder().name("PR_Champion").build());
		stage = persist(Stage.builder().name("PR Playoffs").stageOrder(96)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
				.regular(false).build());
	}

	@ParameterizedTest
	@CsvSource({
			"overallScore,PR_First", "avgRating,PR_Second", "sets,PR_First",
			"COMBAT,PR_Second", "DAMAGE,PR_Second", "LANE,PR_Second",
			"GROWTH,PR_First", "VISION,PR_First", "SURVIVAL,PR_First"
	})
	@DisplayName("종합 점수와 평점 및 세트 수와 여섯 영역으로 정렬합니다.")
	void sortsByEverySupportedValue(String sort, String firstName) throws Exception {
		Player first = player("PR_First", Position.TOP);
		Player second = player("PR_Second", Position.MID);
		records(first, 11, 50);
		records(second, 10, 100);
		season(first, 11, 95, 10, 20, 30, 40, 50, 60);
		season(second, 10, 85, 60, 50, 40, 30, 20, 10);

		request(list().param("sort", sort))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].name").value(firstName))
				.andExpect(jsonPath("$.content[0].rank").value(1))
				.andExpect(jsonPath("$.content[1].rank").value(2));
	}

	@Test
	@DisplayName("실명 대신 닉네임과 현재 팀을 반환하고 영역 점수를 올바르게 대응합니다.")
	void mapsResponseAndCurrentTeam() throws Exception {
		Player player = player("PR_Display", Position.SUPPORT);
		records(player, 10, 50);
		season(player, 10, 85, 10, 20, 30, 40, 50, 60);
		player.changeTeam(opponent);

		request(list())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].playerId").value(player.getId()))
				.andExpect(jsonPath("$.content[0].name").value("PR_Display"))
				.andExpect(jsonPath("$.content[0].position").value("SUPPORT"))
				.andExpect(jsonPath("$.content[0].team.teamId").value(opponent.getId()))
				.andExpect(jsonPath("$.content[0].team.code").value("PRO"))
				.andExpect(jsonPath("$.content[0].team.name").doesNotExist())
				.andExpect(jsonPath("$.content[0].sets").value(10))
				.andExpect(jsonPath("$.content[0].avgRating").value(6.0))
				.andExpect(jsonPath("$.content[0].overallScore").value(85.0))
				.andExpect(jsonPath("$.content[0].grade").value("A"))
				.andExpect(jsonPath("$.content[0].areas.length()").value(6))
				.andExpect(jsonPath("$.content[0].areas[0].area").value("COMBAT"))
				.andExpect(jsonPath("$.content[0].areas[0].score").value(10.0))
				.andExpect(jsonPath("$.content[0].areas[0].grade").value("C"))
				.andExpect(jsonPath("$.content[0].areas[1].area").value("DAMAGE"))
				.andExpect(jsonPath("$.content[0].areas[1].score").value(20.0))
				.andExpect(jsonPath("$.content[0].areas[2].area").value("LANE"))
				.andExpect(jsonPath("$.content[0].areas[2].score").value(30.0))
				.andExpect(jsonPath("$.content[0].areas[3].area").value("GROWTH"))
				.andExpect(jsonPath("$.content[0].areas[3].score").value(40.0))
				.andExpect(jsonPath("$.content[0].areas[4].area").value("VISION"))
				.andExpect(jsonPath("$.content[0].areas[4].score").value(50.0))
				.andExpect(jsonPath("$.content[0].areas[5].area").value("SURVIVAL"))
				.andExpect(jsonPath("$.content[0].areas[5].score").value(60.0))
				.andExpect(jsonPath("$.content[0].lowSample").value(false))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	@DisplayName("동점은 공동 순위로 표시하고 페이지가 바뀌어도 전체 순위를 유지합니다.")
	void ranksBeforePaginationAndKeepsTiesStable() throws Exception {
		Player first = player("PR_TieOne", Position.MID);
		Player second = player("PR_TieTwo", Position.MID);
		Player third = player("PR_Third", Position.MID);
		for (Player player : new Player[]{first, second, third}) records(player, 10, 50);
		season(first, 10, 90);
		season(second, 10, 90);
		season(third, 10, 80);

		request(list().param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].playerId").value(second.getId()))
				.andExpect(jsonPath("$.content[0].rank").value(1))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(3));
		request(list().param("page", "2").param("size", "1"))
				.andExpect(jsonPath("$.content[0].rank").value(3));
		request(list().param("page", "2147483647").param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	@DisplayName("표본 부족은 기본적으로 제외하고 포함할 때 점수와 등급 없이 마지막에 표시합니다.")
	void lowSamplesAreOptionalAndAlwaysLast() throws Exception {
		Player regular = player("PR_Regular", Position.TOP);
		Player shortSample = player("PR_Short", Position.MID);
		Player noRecords = player("PR_Zero", Position.ADC);
		records(regular, 10, 50);
		records(shortSample, 9, 100);
		season(regular, 10, 0);
		season(shortSample, 15, 100);

		request(list()).andExpect(jsonPath("$.totalElements").value(1));
		request(list().param("includeLowSample", "true").param("sort", "avgRating"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.content[0].playerId").value(regular.getId()))
				.andExpect(jsonPath("$.content[0].grade").value("C"))
				.andExpect(jsonPath("$.content[1].playerId").value(shortSample.getId()))
				.andExpect(jsonPath("$.content[1].rank").value(nullValue()))
				.andExpect(jsonPath("$.content[1].overallScore").value(nullValue()))
				.andExpect(jsonPath("$.content[1].grade").value(nullValue()))
				.andExpect(jsonPath("$.content[1].areas[0].score").value(nullValue()))
				.andExpect(jsonPath("$.content[1].areas[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.content[1].lowSample").value(true))
				.andExpect(jsonPath("$.content[2].playerId").value(noRecords.getId()))
				.andExpect(jsonPath("$.content[2].sets").value(0))
				.andExpect(jsonPath("$.content[2].avgRating").value(nullValue()));
	}

	@Test
	@DisplayName("시즌 점수가 없거나 오래된 집계여도 실제 10세트 선수의 기본정보는 조회됩니다.")
	void missingSeasonScoresRemainNullAndLast() throws Exception {
		Player known = player("PR_Known", Position.TOP);
		Player absent = player("PR_Absent", Position.MID);
		Player stale = player("PR_Stale", Position.ADC);
		Player nullPoints = player("PR_Null", Position.SUPPORT);
		for (Player player : new Player[]{known, absent, stale, nullPoints}) records(player, 10, null);
		season(known, 10, 75);
		season(stale, 9, 100);
		season(nullPoints, 10, null);

		request(list())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.content[0].playerId").value(known.getId()))
				.andExpect(jsonPath("$.content[0].avgRating").value(nullValue()))
				.andExpect(jsonPath("$.content[1].playerId").value(absent.getId()))
				.andExpect(jsonPath("$.content[1].rank").value(nullValue()))
				.andExpect(jsonPath("$.content[1].grade").value(nullValue()))
				.andExpect(jsonPath("$.content[1].lowSample").value(false))
				.andExpect(jsonPath("$.content[2].overallScore").value(nullValue()))
				.andExpect(jsonPath("$.content[3].overallScore").value(nullValue()));
	}

	@Test
	@DisplayName("세트 수에는 점수 없는 기록도 포함하고 평균 평점에서만 제외합니다.")
	void averagesAvailableRatingsWithCommonFormula() throws Exception {
		Player player = player("PR_Rating", Position.JUNGLE);
		records(player, 8, null);
		record(player, LocalDate.of(2026, 1, 1), 80);
		record(player, LocalDate.of(2026, 12, 31), 85);
		season(player, 10, 90);

		request(list())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].sets").value(10))
				.andExpect(jsonPath("$.content[0].avgRating").value(8.6));
	}

	@Test
	@DisplayName("2025년과 2027년 기록은 2026 시즌 출전 조건에 포함하지 않습니다.")
	void ignoresOtherYearsEvenWhenCacheIncludesThem() throws Exception {
		Player player = player("PR_Year", Position.TOP);
		records(player, 9, 50);
		record(player, LocalDate.of(2025, 12, 31), 100);
		record(player, LocalDate.of(2027, 1, 1), 100);
		season(player, 11, 95);

		request(list()).andExpect(jsonPath("$.content").isEmpty());
		request(list().param("includeLowSample", "true"))
				.andExpect(jsonPath("$.content[0].sets").value(9))
				.andExpect(jsonPath("$.content[0].grade").value(nullValue()));
	}

	@Test
	@DisplayName("검색은 대소문자를 무시하며 퍼센트와 밑줄은 글자 그대로 찾습니다.")
	void combinesPositionAndLiteralNicknameSearch() throws Exception {
		Player literal = player("PR_%Name", Position.MID);
		Player regular = player("PR_Name", Position.TOP);
		Player otherPosition = player("PR_%Other", Position.TOP);
		for (Player player : new Player[]{literal, regular, otherPosition}) {
			records(player, 10, 50);
			season(player, 10, 85);
		}
		request(list().param("q", "  %nAmE  ").param("position", "MID"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].playerId").value(literal.getId()))
				.andExpect(jsonPath("$.content[0].rank").value(1));
		request(list().param("q", "no match"))
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalPages").value(0));
	}

	@ParameterizedTest
	@CsvSource({
			"sort,unknown", "position,BOT", "position,invalid", "page,-1",
			"size,0", "size,101", "page,abc", "includeLowSample,invalid"
	})
	@DisplayName("지원하지 않는 정렬과 포지션 및 잘못된 페이지 입력은 공통 400입니다.")
	void rejectsInvalidInput(String key, String value) throws Exception {
		request(list().param(key, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	@DisplayName("선수 데이터가 없으면 올바른 빈 페이지를 반환합니다.")
	void emptyDatabaseReturnsEmptyPage() throws Exception {
		request(list())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
	}

	private Player player(String nickname, Position position) {
		return persist(Player.builder().team(team).nickname(nickname).name("실명")
				.position(position).build());
	}

	private void records(Player player, int count, Integer points) {
		for (int i = 0; i < count; i++) record(player, LocalDate.of(2026, 4, 1).plusDays(i), points);
	}

	private void record(Player player, LocalDate date, Integer points) {
		LckMatch match = persist(LckMatch.builder().team1(team).team2(opponent)
				.stage(stage).sets(3).matchTime(date.atTime(17, 0)).build());
		MatchSet set = persist(MatchSet.builder().match(match).setNumber(1)
				.gameId("PR_GAME_" + ++gameNumber).version("26.08").playTime(LocalTime.of(0, 30)).build());
		TeamSet teamSet = persist(TeamSet.builder().team(team).matchSet(set).side(Side.BLUE).victory(true).build());
		persist(PlayerSet.builder().player(player).teamSet(teamSet).champion(champion)
				.position(player.getPosition()).totalPoints(points).build());
	}

	private void season(Player player, int sets, Integer points, int... areas) {
		Integer[] scores = new Integer[6];
		for (int i = 0; i < areas.length; i++) scores[i] = areas[i];
		persist(PlayerSeason.builder().player(player).sets(sets).totalPoints(points)
				.combatPoints(scores[0]).damagePoints(scores[1]).laningPoints(scores[2])
				.growthPoints(scores[3]).wardPoints(scores[4]).survivalPoints(scores[5])
				.calculatedAt(LocalDateTime.of(2026, 11, 19, 12, 0)).build());
	}

	private MockHttpServletRequestBuilder list() {
		return get("/api/v1/players");
	}

	private ResultActions request(MockHttpServletRequestBuilder request) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(request);
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}
}
