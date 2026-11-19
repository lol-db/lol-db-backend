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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 실제 MySQL 기록을 저장하여 선수 상세 API를 검증하며, 각 검사 후 데이터를 롤백합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerControllerTests {

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;

	private Team originalTeam;
	private Team otherTeam;
	private Player player;
	private Player teammate;
	private Player opposingMid;
	private Champion championA;
	private Champion championB;
	private Stage stage;
	private int gameNumber;

	@BeforeEach
	void setUp() {
		originalTeam = persist(Team.builder().code("PDA").build());
		otherTeam = persist(Team.builder().code("PDB").build());
		player = persist(Player.builder().team(originalTeam).nickname("PD_Target")
				.position(Position.MID).name("선수실명").build());
		teammate = persist(Player.builder().team(originalTeam).nickname("PD_Top")
				.position(Position.TOP).build());
		opposingMid = persist(Player.builder().team(otherTeam).nickname("PD_Opponent")
				.position(Position.MID).build());
		championA = persist(Champion.builder().name("PDChampA").build());
		championB = persist(Champion.builder().name("PDChampB").build());
		stage = persist(Stage.builder().name("PD Regular").stageOrder(97)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
				.regular(true).build());
	}

	@Test
	@DisplayName("세트 집계는 조인으로 중복되지 않으며 팀 킬은 경기 당시 소속 팀에서 구합니다")
	void aggregatesSetsAndHistoricalTeamKillsWithoutJoinMultiplication() throws Exception {
		addSet("2026-04-01", "26.08", championA, 1200, true,
				stats(4, 0, 6, 100, 20, 50, true), 6, 80);
		addSet("2026-04-03", "26.08", championB, 2400, false,
				stats(2, 3, 1, 360, 40, 88, false), 3, 320);
		Team currentTeam = persist(Team.builder().code("PDC").build());
		player.changeTeam(currentTeam);

		request(detail())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerId").value(player.getId()))
				.andExpect(jsonPath("$.name").value("PD_Target"))
				.andExpect(jsonPath("$.position").value("MID"))
				.andExpect(jsonPath("$.team.teamId").value(currentTeam.getId()))
				.andExpect(jsonPath("$.team.code").value("PDC"))
				.andExpect(jsonPath("$.team.name").doesNotExist())
				.andExpect(jsonPath("$.filter.patch").value(nullValue()))
				.andExpect(jsonPath("$.filter.championId").value(nullValue()))
				.andExpect(jsonPath("$.filter.fromDate").value("2026-04-01"))
				.andExpect(jsonPath("$.filter.toDate").value("2026-04-03"))
				.andExpect(jsonPath("$.summary.sets").value(2))
				.andExpect(jsonPath("$.summary.wins").value(1))
				.andExpect(jsonPath("$.summary.winRate").value(0.5))
				.andExpect(jsonPath("$.summary.kills").value(6))
				.andExpect(jsonPath("$.summary.deaths").value(3))
				.andExpect(jsonPath("$.summary.assists").value(7))
				.andExpect(jsonPath("$.summary.kda").value(4.33))
				.andExpect(jsonPath("$.summary.teamKills").value(15))
				.andExpect(jsonPath("$.summary.killParticipation").value(0.8667))
				// 개인 CSM은 (6 + 10) / 2이며, 경기 시간을 합산한 비율과 다릅니다.
				.andExpect(jsonPath("$.summary.csPerMin").value(8.0))
				.andExpect(jsonPath("$.summary.positionAvgCsPerMin").value(7.0))
				// 50점과 88점은 각각 6.0점과 9.0점의 평점으로 변환됩니다.
				.andExpect(jsonPath("$.summary.avgRating").value(7.5))
				.andExpect(jsonPath("$.summary.bestRating").value(9.0))
				.andExpect(jsonPath("$.summary.pogCount").value(1))
				.andExpect(jsonPath("$.gradeAvailable").value(false))
				.andExpect(jsonPath("$.grade").value(nullValue()));
	}

	@Test
	@DisplayName("패치와 챔피언 필터는 함께 적용하고 동료의 킬에는 챔피언 필터를 적용하지 않습니다")
	void appliesBothFiltersButIncludesTeammatesUsingOtherChampions() throws Exception {
		addSet("2026-04-01", "26.08", championA, 1200, true,
				stats(2, 1, 3, 100, 20, 50, true), 8, 80);
		addSet("2026-04-02", "26.08", championB, 1800, false,
				stats(20, 10, 10, 300, 0, 100, false), 20, 240);
		addSet("2026-04-03", "26.09", championA, 1800, false,
				stats(30, 10, 10, 300, 0, 100, false), 20, 240);

		request(detail().param("patch", " 26.08 ").param("championId", championA.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filter.patch").value("26.08"))
				.andExpect(jsonPath("$.filter.championId").value(championA.getId()))
				.andExpect(jsonPath("$.filter.fromDate").value("2026-04-01"))
				.andExpect(jsonPath("$.filter.toDate").value("2026-04-01"))
				.andExpect(jsonPath("$.summary.sets").value(1))
				.andExpect(jsonPath("$.summary.wins").value(1))
				.andExpect(jsonPath("$.summary.kills").value(2))
				.andExpect(jsonPath("$.summary.deaths").value(1))
				.andExpect(jsonPath("$.summary.assists").value(3))
				.andExpect(jsonPath("$.summary.teamKills").value(10))
				.andExpect(jsonPath("$.summary.killParticipation").value(0.5))
				.andExpect(jsonPath("$.summary.csPerMin").value(6.0))
				.andExpect(jsonPath("$.summary.positionAvgCsPerMin").value(6.0))
				.andExpect(jsonPath("$.summary.avgRating").value(6.0))
				.andExpect(jsonPath("$.summary.pogCount").value(1));
	}

	@Test
	@DisplayName("패치만 지정하면 해당 패치의 모든 챔피언 기록을 집계합니다")
	void patchFilterWorksWithoutChampionFilter() throws Exception {
		addSet("2026-04-01", "26.08", championA, 1200, true, stats(1, 1, 1, 120, 0, 50, false), 4, 80);
		addSet("2026-04-02", "26.08", championB, 1200, false, stats(2, 1, 1, 120, 0, 50, false), 4, 80);
		addSet("2026-04-03", "26.09", championA, 1200, true, stats(30, 1, 1, 120, 0, 50, false), 4, 80);

		request(detail().param("patch", "26.08"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.sets").value(2))
				.andExpect(jsonPath("$.summary.kills").value(3));
	}

	@Test
	@DisplayName("챔피언만 지정하면 패치와 관계없이 해당 챔피언 기록을 집계합니다")
	void championFilterWorksWithoutPatchFilter() throws Exception {
		addSet("2026-04-01", "26.08", championA, 1200, true, stats(1, 1, 1, 120, 0, 50, false), 4, 80);
		addSet("2026-04-02", "26.09", championA, 1200, false, stats(2, 1, 1, 120, 0, 50, false), 4, 80);
		addSet("2026-04-03", "26.08", championB, 1200, true, stats(30, 1, 1, 120, 0, 50, false), 4, 80);

		request(detail().param("championId", championA.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.sets").value(2))
				.andExpect(jsonPath("$.summary.kills").value(3));
	}

	@Test
	@DisplayName("선수가 존재하지만 기록이 없으면 횟수는 0이고 계산할 수 없는 값은 null입니다")
	void existingPlayerWithoutRecordsHasEmptySummary() throws Exception {
		assertEmptySummary(request(detail()))
				.andExpect(jsonPath("$.name").value("PD_Target"));
	}

	@Test
	@DisplayName("본인의 출전 기록이 없어도 같은 포지션의 리그 비교 평균은 반환합니다")
	void playerWithoutRecordsStillReceivesAvailablePositionComparison() throws Exception {
		addSet("2026-04-01", "26.08", championA, 1200, true,
				stats(2, 1, 3, 100, 20, 50, false), 8, 80);
		Player idlePlayer = persist(Player.builder().team(originalTeam).nickname("PD_Idle")
				.position(Position.MID).build());

		assertEmptySummary(request(get("/api/v1/players/{playerId}", idlePlayer.getId())), 5.0);
	}

	@Test
	@DisplayName("필터와 일치하는 기록이 없으면 다른 선수 기록으로 값을 채우지 않습니다")
	void filtersWithNoMatchesReturnEmptySummary() throws Exception {
		addSet("2026-04-01", "26.08", championA, 1200, true, stats(2, 1, 3, 120, 0, 50, false), 8, 80);

		assertEmptySummary(request(detail().param("patch", "26.99")))
				.andExpect(jsonPath("$.filter.patch").value("26.99"));
		assertEmptySummary(request(detail().param("championId", Long.toString(Long.MAX_VALUE))))
				.andExpect(jsonPath("$.filter.championId").value(Long.MAX_VALUE));
	}

	@Test
	@DisplayName("0데스의 KDA는 분모 1로 계산하며 분당 CS는 소수 둘째 자리로 반올림합니다")
	void zeroDeathsUsesOneAndCsIsRounded() throws Exception {
		addSet("2026-04-01", "26.08", championA, 1800, true,
				stats(3, 0, 2, 100, 0, 50, false), 7, 120);

		request(detail())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.kda").value(5.0))
				.andExpect(jsonPath("$.summary.csPerMin").value(3.33))
				.andExpect(jsonPath("$.summary.positionAvgCsPerMin").value(3.67));
	}

	@Test
	@DisplayName("팀 킬과 경기 시간이 0이면 킬 관여율과 분당 CS를 null로 반환합니다")
	void zeroTeamKillsAndDurationDoNotProduceNonFiniteNumbers() throws Exception {
		addSet("2026-04-01", "26.08", championA, 0, false,
				stats(0, 0, 0, 100, 0, null, false), 0, 100);

		request(detail())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.sets").value(1))
				.andExpect(jsonPath("$.summary.wins").value(0))
				.andExpect(jsonPath("$.summary.winRate").value(0.0))
				.andExpect(jsonPath("$.summary.kda").value(0.0))
				.andExpect(jsonPath("$.summary.teamKills").value(0))
				.andExpect(jsonPath("$.summary.killParticipation").value(nullValue()))
				.andExpect(jsonPath("$.summary.csPerMin").value(nullValue()))
				.andExpect(jsonPath("$.summary.positionAvgCsPerMin").value(nullValue()))
				.andExpect(jsonPath("$.summary.avgRating").value(nullValue()))
				.andExpect(jsonPath("$.summary.bestRating").value(nullValue()));
	}

	@Test
	@DisplayName("미계산 평점과 0초 기록은 각각의 평균 분모에서 제외합니다")
	void ignoresUncalculatedRatingsAndZeroDurationOnlyForRelevantAverages() throws Exception {
		addSet("2026-04-01", "26.08", championA, 0, true,
				stats(2, 1, 1, 999, 0, null, false), 8, 999);
		addSet("2026-04-02", "26.08", championA, 1200, false,
				stats(2, 1, 1, 100, 20, 50, false), 8, 80);
		addSet("2026-04-03", "26.08", championA, 1200, false,
				stats(2, 1, 1, 100, 20, 51, false), 8, 80);

		request(detail())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.sets").value(3))
				.andExpect(jsonPath("$.summary.kills").value(6))
				.andExpect(jsonPath("$.summary.winRate").value(0.3333))
				.andExpect(jsonPath("$.summary.csPerMin").value(6.0))
				.andExpect(jsonPath("$.summary.positionAvgCsPerMin").value(5.0))
				// 변환된 6.0점과 6.1점의 평균 6.05는 6.1점으로 반올림합니다.
				.andExpect(jsonPath("$.summary.avgRating").value(6.1))
				.andExpect(jsonPath("$.summary.bestRating").value(6.1));
	}

	@Test
	@DisplayName("2026년 시작과 끝은 포함하고 2025년과 2027년 기록은 집계에서 제외합니다")
	void includesOnlyThe2026Season() throws Exception {
		addSet("2025-12-31", "26.08", championA, 1200, true, stats(90, 90, 90, 900, 0, 100, true), 10, 900);
		addSet("2026-01-01", "26.08", championA, 1200, true, stats(1, 1, 1, 120, 0, 50, false), 4, 80);
		addSet("2026-12-31", "26.08", championA, 1200, false, stats(2, 1, 1, 120, 0, 50, false), 4, 80);
		addSet("2027-01-01", "26.08", championA, 1200, true, stats(90, 90, 90, 900, 0, 100, true), 10, 900);

		request(detail())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filter.fromDate").value("2026-01-01"))
				.andExpect(jsonPath("$.filter.toDate").value("2026-12-31"))
				.andExpect(jsonPath("$.summary.sets").value(2))
				.andExpect(jsonPath("$.summary.wins").value(1))
				.andExpect(jsonPath("$.summary.kills").value(3))
				.andExpect(jsonPath("$.summary.teamKills").value(11))
				.andExpect(jsonPath("$.summary.csPerMin").value(6.0))
				.andExpect(jsonPath("$.summary.positionAvgCsPerMin").value(5.0))
				.andExpect(jsonPath("$.summary.avgRating").value(6.0))
				.andExpect(jsonPath("$.summary.pogCount").value(0));
	}

	@ParameterizedTest
	@CsvSource({"95,A+", "85,A", "75,A-", "60,B+", "45,B", "30,B-", "15,C+", "14,C"})
	@DisplayName("10세트와 계산된 시즌 점수가 있으면 백분위 경계에 맞는 등급을 반환합니다")
	void seasonGradesRespectPercentileBoundaries(int totalPoints, String grade) throws Exception {
		addRegularSets(10);
		addSeason(10, totalPoints);

		request(detail())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.sets").value(10))
				.andExpect(jsonPath("$.gradeAvailable").value(true))
				.andExpect(jsonPath("$.grade").value(grade));
	}

	@Test
	@DisplayName("전체 시즌 등급이 있어도 필터가 있으면 숨기며 공백 패치는 필터로 취급하지 않습니다")
	void activeFiltersSuppressGradeButBlankPatchDoesNot() throws Exception {
		addRegularSets(10);
		addSeason(10, 95);

		assertGradeUnavailable(request(detail().param("patch", "26.08")));
		assertGradeUnavailable(request(detail().param("championId", championA.getId().toString())));
		request(detail().param("patch", "   "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filter.patch").value(nullValue()))
				.andExpect(jsonPath("$.summary.sets").value(10))
				.andExpect(jsonPath("$.gradeAvailable").value(true))
				.andExpect(jsonPath("$.grade").value("A+"));
	}

	@Test
	@DisplayName("다른 연도를 합쳐 10세트가 되어도 2026년 기록이 9세트이면 등급을 숨깁니다")
	void nineCurrentSeasonSetsDoNotQualifyDespiteSavedSeasonCount() throws Exception {
		addRegularSets(9);
		addSet("2025-12-31", "26.08", championA, 1200, true, stats(1, 1, 1, 120, 0, 50, false), 4, 80);
		addSet("2027-01-01", "26.08", championA, 1200, true, stats(1, 1, 1, 120, 0, 50, false), 4, 80);
		addSeason(11, 95);

		assertGradeUnavailable(request(detail()))
				.andExpect(jsonPath("$.summary.sets").value(9));
	}

	@ParameterizedTest
	@CsvSource(value = {"9,95", "10,NULL"}, nullValues = "NULL")
	@DisplayName("시즌 집계가 10세트 미만이거나 시즌 점수가 없으면 등급을 숨깁니다")
	void incompleteSeasonCalculationDoesNotQualify(int savedSets, Integer totalPoints) throws Exception {
		addRegularSets(10);
		addSeason(savedSets, totalPoints);

		assertGradeUnavailable(request(detail()))
				.andExpect(jsonPath("$.summary.sets").value(10));
	}

	@Test
	@DisplayName("실제 기록이 10세트여도 시즌 집계 행이 없으면 등급을 숨깁니다")
	void missingSeasonCalculationDoesNotQualify() throws Exception {
		addRegularSets(10);
		assertGradeUnavailable(request(detail()))
				.andExpect(jsonPath("$.summary.sets").value(10));
	}

	@ParameterizedTest
	@EnumSource(Position.class)
	@DisplayName("포지션은 현재 DB 열거형 코드를 그대로 반환합니다")
	void returnsActualPositionCodes(Position position) throws Exception {
		Player another = persist(Player.builder().team(originalTeam).nickname("PD_Code")
				.position(position).build());

		request(get("/api/v1/players/{playerId}", another.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.position").value(position.name()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
	@DisplayName("잘못된 선수 번호는 공통 입력 오류로 응답합니다")
	void invalidPlayerIdReturnsBadRequest(String playerId) throws Exception {
		request(get("/api/v1/players/{playerId}", playerId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
	@DisplayName("잘못된 챔피언 번호는 공통 입력 오류로 응답합니다")
	void invalidChampionIdReturnsBadRequest(String championId) throws Exception {
		request(detail().param("championId", championId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	@DisplayName("존재하지 않는 선수는 선수 없음 오류로 응답합니다")
	void unknownPlayerReturnsNotFound() throws Exception {
		request(get("/api/v1/players/{playerId}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("PLAYER_001"));
	}

	private MockHttpServletRequestBuilder detail() {
		return get("/api/v1/players/{playerId}", player.getId());
	}

	private ResultActions request(MockHttpServletRequestBuilder builder) throws Exception {
		// 저장된 관계를 실제 조회하도록 영속성 컨텍스트를 비웁니다.
		em.flush();
		em.clear();
		return mockMvc.perform(builder);
	}

	private ResultActions assertEmptySummary(ResultActions result) throws Exception {
		return assertEmptySummary(result, null);
	}

	private ResultActions assertEmptySummary(ResultActions result, Double positionAverage) throws Exception {
		return assertGradeUnavailable(result)
				.andExpect(jsonPath("$.summary.sets").value(0))
				.andExpect(jsonPath("$.summary.wins").value(0))
				.andExpect(jsonPath("$.summary.kills").value(0))
				.andExpect(jsonPath("$.summary.deaths").value(0))
				.andExpect(jsonPath("$.summary.assists").value(0))
				.andExpect(jsonPath("$.summary.teamKills").value(0))
				.andExpect(jsonPath("$.summary.pogCount").value(0))
				.andExpect(jsonPath("$.summary.winRate").value(nullValue()))
				.andExpect(jsonPath("$.summary.kda").value(nullValue()))
				.andExpect(jsonPath("$.summary.killParticipation").value(nullValue()))
				.andExpect(jsonPath("$.summary.csPerMin").value(nullValue()))
				.andExpect(positionAverage == null
						? jsonPath("$.summary.positionAvgCsPerMin").value(nullValue())
						: jsonPath("$.summary.positionAvgCsPerMin").value(positionAverage))
				.andExpect(jsonPath("$.summary.avgRating").value(nullValue()))
				.andExpect(jsonPath("$.summary.bestRating").value(nullValue()))
				.andExpect(jsonPath("$.filter.fromDate").value(nullValue()))
				.andExpect(jsonPath("$.filter.toDate").value(nullValue()));
	}

	private ResultActions assertGradeUnavailable(ResultActions result) throws Exception {
		return result.andExpect(status().isOk())
				.andExpect(jsonPath("$.gradeAvailable").value(false))
				.andExpect(jsonPath("$.grade").value(nullValue()));
	}

	private void addRegularSets(int count) {
		for (int index = 0; index < count; index++) {
			addSet(LocalDate.of(2026, 4, 1).plusDays(index).toString(), "26.08", championA,
					1200, true, stats(2, 1, 3, 100, 20, 50, false), 8, 80);
		}
	}

	private void addSeason(int sets, Integer totalPoints) {
		persist(PlayerSeason.builder().player(player).sets(sets).totalPoints(totalPoints)
				.calculatedAt(LocalDateTime.of(2026, 11, 19, 0, 0)).build());
	}

	private void addSet(String date, String patch, Champion champion, int seconds, boolean victory,
			SetStats targetStats, int teammateKills, int opposingCs) {
		LckMatch match = persist(LckMatch.builder().matchTime(LocalDate.parse(date).atTime(17, 0))
				.sets(3).team1(originalTeam).team2(otherTeam).stage(stage).build());
		MatchSet set = persist(MatchSet.builder().match(match).setNumber(1)
				.gameId("PD-GAME-" + ++gameNumber).version(patch)
				.playTime(LocalTime.ofSecondOfDay(seconds)).build());
		TeamSet teamSet = persist(TeamSet.builder().matchSet(set).team(originalTeam)
				.side(Side.BLUE).victory(victory).build());
		TeamSet opposingTeamSet = persist(TeamSet.builder().matchSet(set).team(otherTeam)
				.side(Side.RED).victory(!victory).build());
		persist(PlayerSet.builder().teamSet(teamSet).player(player).position(Position.MID)
				.champion(champion).kills(targetStats.kills()).deaths(targetStats.deaths())
				.assists(targetStats.assists()).minionKills(targetStats.minions())
				.monsterKills(targetStats.monsters()).totalPoints(targetStats.points())
				.pog(targetStats.pog()).build());
		// 같은 팀의 다른 챔피언과 다른 포지션을 넣어 분모 및 포지션 필터를 확인합니다.
		persist(PlayerSet.builder().teamSet(teamSet).player(teammate).position(Position.TOP)
				.champion(championB).kills(teammateKills).minionKills(1000).build());
		persist(PlayerSet.builder().teamSet(opposingTeamSet).player(opposingMid).position(Position.MID)
				.champion(championB).kills(5).minionKills(opposingCs).build());
	}

	private static SetStats stats(int kills, int deaths, int assists, int minions, int monsters,
			Integer points, boolean pog) {
		return new SetStats(kills, deaths, assists, minions, monsters, points, pog);
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}

	private record SetStats(int kills, int deaths, int assists, int minions, int monsters,
			Integer points, boolean pog) {
	}
}
