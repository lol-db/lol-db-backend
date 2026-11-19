package com.riftdata.domain.player.controller;

import static org.hamcrest.Matchers.closeTo;
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
import java.util.ArrayList;
import java.util.List;
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

/** API-14의 실제 DB 집계와 HTTP 응답을 확인하며 검사 데이터는 롤백됩니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerOverviewControllerTests {
	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;
	private Team originalTeam;
	private Team opposingTeam;
	private Player player;
	private Player teammate;
	private Player enemy;
	private Champion championA;
	private Champion championB;
	private Stage stage;
	private int gameNumber;

	@BeforeEach
	void setUp() {
		originalTeam = persist(Team.builder().code("POA").build());
		opposingTeam = persist(Team.builder().code("POB").build());
		player = player("PO_Player", Position.MID);
		teammate = player("PO_Mate", Position.TOP);
		enemy = persist(Player.builder().team(opposingTeam).nickname("PO_Enemy").position(Position.MID).build());
		championA = persist(Champion.builder().name("PO_ChampionA").build());
		championB = persist(Champion.builder().name("PO_ChampionB").build());
		stage = persist(Stage.builder().name("PO Playoffs").stageOrder(95)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
				.regular(false).build());
	}

	@Test
	@DisplayName("저장된 종합 점수와 영역 점수 및 가중치를 재계산하지 않고 모든 응답 영역에 전달합니다.")
	void returnsAllOverviewSectionsAndStoredScores() throws Exception {
		records(player, 10, sample());
		season(player, 10, 95);

		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.compareGroup.position").value("MID"))
				.andExpect(jsonPath("$.compareGroup.playerCount").value(1))
				.andExpect(jsonPath("$.compareGroup.minSets").value(10))
				.andExpect(jsonPath("$.overall.score").value(95.0))
				.andExpect(jsonPath("$.overall.grade").value("A+"))
				.andExpect(jsonPath("$.overall.topPercent").value(5.0))
				.andExpect(jsonPath("$.overall.positionRank").value(1))
				.andExpect(jsonPath("$.areas.length()").value(6))
				.andExpect(jsonPath("$.areas[0].area").value("COMBAT"))
				.andExpect(jsonPath("$.areas[0].score").value(80.0))
				.andExpect(jsonPath("$.areas[0].grade").value("A-"))
				.andExpect(jsonPath("$.areas[0].topPercent").value(20.0))
				.andExpect(jsonPath("$.areas[0].weight").value(25))
				.andExpect(jsonPath("$.areas[1].score").value(70.0))
				.andExpect(jsonPath("$.areas[1].weight").value(25))
				.andExpect(jsonPath("$.areas[2].score").value(60.0))
				.andExpect(jsonPath("$.areas[2].weight").value(20))
				.andExpect(jsonPath("$.areas[3].score").value(50.0))
				.andExpect(jsonPath("$.areas[3].weight").value(15))
				.andExpect(jsonPath("$.areas[4].score").value(40.0))
				.andExpect(jsonPath("$.areas[4].weight").value(5))
				.andExpect(jsonPath("$.areas[5].score").value(30.0))
				.andExpect(jsonPath("$.areas[5].weight").value(10))
				.andExpect(jsonPath("$.mostChampions[0].sets").value(10))
				.andExpect(jsonPath("$.mostChampions[0].avgRating").value(6.0))
				.andExpect(jsonPath("$.keyMetrics.length()").value(10))
				.andExpect(jsonPath("$.keyMetrics[0].code").value("DPM"))
				.andExpect(jsonPath("$.keyMetrics[0].value").value(500.0))
				.andExpect(jsonPath("$.keyMetrics[9].code").value("D10"))
				.andExpect(jsonPath("$.keyMetrics[9].direction").value(-1))
				.andExpect(jsonPath("$.recentSets.length()").value(5))
				.andExpect(jsonPath("$.ratingTrend.length()").value(10));
	}

	@Test
	@DisplayName("패치와 챔피언 필터는 함께 적용하며 시즌 등급 영역을 숨기고 원시 지표는 유지합니다.")
	void filtersUseAndConditionAndSuppressSeasonGrades() throws Exception {
		for (int i = 0; i < 10; i++) {
			addSet(player, i < 7 ? "26.08" : "26.09", i < 4 || i >= 7 ? championA : championB,
					LocalDate.of(2026, 4, 1).plusDays(i), sample(), null);
		}
		season(player, 10, 95);

		request(overview().param("patch", "26.08").param("championId", championA.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall").value(nullValue()))
				.andExpect(jsonPath("$.areas").value(nullValue()))
				.andExpect(jsonPath("$.mostChampions.length()").value(1))
				.andExpect(jsonPath("$.mostChampions[0].sets").value(4))
				.andExpect(jsonPath("$.ratingTrend.length()").value(4))
				.andExpect(jsonPath("$.keyMetrics[0].value").value(500.0))
				.andExpect(jsonPath("$.keyMetrics[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[0].topPercent").value(nullValue()));
		request(overview().param("patch", "  "))
				.andExpect(jsonPath("$.overall.grade").value("A+"))
				.andExpect(jsonPath("$.ratingTrend.length()").value(10));
	}

	@Test
	@DisplayName("알 수 없는 필터는 오류 대신 빈 기록과 계산 불가 값을 반환합니다.")
	void unknownFilterReturnsEmptyRecordSections() throws Exception {
		records(player, 10, sample());
		season(player, 10, 95);
		request(overview().param("patch", "unknown"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall").value(nullValue()))
				.andExpect(jsonPath("$.areas").value(nullValue()))
				.andExpect(jsonPath("$.mostChampions").isEmpty())
				.andExpect(jsonPath("$.recentSets").isEmpty())
				.andExpect(jsonPath("$.ratingTrend").isEmpty())
				.andExpect(jsonPath("$.keyMetrics[0].value").value(nullValue()));
	}

	@Test
	@DisplayName("실제 출전이 9세트이면 저장된 시즌 점수가 있어도 점수와 등급을 숨깁니다.")
	void insufficientActualSetsKeepValuesButHideScores() throws Exception {
		records(player, 9, sample());
		season(player, 10, 100);
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall").value(nullValue()))
				.andExpect(jsonPath("$.areas.length()").value(6))
				.andExpect(jsonPath("$.areas[0].score").value(nullValue()))
				.andExpect(jsonPath("$.areas[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[0].value").value(500.0))
				.andExpect(jsonPath("$.keyMetrics[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[0].topPercent").value(nullValue()));
	}

	@Test
	@DisplayName("출전 기록이 충분해도 시즌 계산 행이 없으면 저장 점수를 임의로 만들지 않습니다.")
	void missingSeasonCacheReturnsNullScores() throws Exception {
		records(player, 10, sample());
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall").value(nullValue()))
				.andExpect(jsonPath("$.areas.length()").value(6))
				.andExpect(jsonPath("$.areas[0].score").value(nullValue()))
				.andExpect(jsonPath("$.areas[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.mostChampions[0].sets").value(10));
	}

	@Test
	@DisplayName("기록 없는 선수는 빈 목록과 값이 없는 열 개의 핵심 지표를 반환합니다.")
	void noRecordsReturnEmptySections() throws Exception {
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.compareGroup.playerCount").value(0))
				.andExpect(jsonPath("$.overall").value(nullValue()))
				.andExpect(jsonPath("$.mostChampions").isEmpty())
				.andExpect(jsonPath("$.recentSets").isEmpty())
				.andExpect(jsonPath("$.ratingTrend").isEmpty())
				.andExpect(jsonPath("$.keyMetrics.length()").value(10))
				.andExpect(jsonPath("$.keyMetrics[0].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[0].positionAvg").value(nullValue()));
	}

	@Test
	@DisplayName("주요 챔피언은 세트 수 상위 네 개이며 같은 횟수는 챔피언 번호로 정렬합니다.")
	void mostChampionsAreTopFourWithStableTies() throws Exception {
		List<Champion> champions = new ArrayList<>(List.of(championA, championB));
		for (int i = 2; i < 6; i++) champions.add(persist(Champion.builder().name("PO_Champ" + i).build()));
		int day = 0;
		int[] counts = {3, 2, 2, 1, 1, 1};
		for (int i = 0; i < counts.length; i++) {
			for (int j = 0; j < counts[i]; j++) addSet(player, "26.08", champions.get(i),
					LocalDate.of(2026, 4, 1).plusDays(day++), sample(), null);
		}
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mostChampions.length()").value(4))
				.andExpect(jsonPath("$.mostChampions[0].championId").value(championA.getId()))
				.andExpect(jsonPath("$.mostChampions[0].sets").value(3))
				.andExpect(jsonPath("$.mostChampions[1].championId").value(championB.getId()))
				.andExpect(jsonPath("$.mostChampions[2].championId").value(champions.get(2).getId()))
				.andExpect(jsonPath("$.mostChampions[3].championId").value(champions.get(3).getId()));
	}

	@Test
	@DisplayName("최근 기록은 다섯 개의 최신순이며 평점 추이는 점수 없는 세트도 시간 순서대로 유지합니다.")
	void recentSetsAndTrendUseDifferentOrderingAndLimits() throws Exception {
		for (int i = 10; i >= 0; i--) {
			Sample values = sample();
			values.points = i == 0 ? null : 50;
			values.pog = i == 10;
			addSet(player, "26.08", championA, LocalDate.of(2026, 4, 1).plusDays(i), values, null);
		}
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recentSets.length()").value(5))
				.andExpect(jsonPath("$.recentSets[0].date").value("2026-04-11"))
				.andExpect(jsonPath("$.recentSets[4].date").value("2026-04-07"))
				.andExpect(jsonPath("$.ratingTrend.length()").value(11))
				.andExpect(jsonPath("$.ratingTrend[0].date").value("2026-04-01"))
				.andExpect(jsonPath("$.ratingTrend[0].rating").value(nullValue()))
				.andExpect(jsonPath("$.ratingTrend[10].date").value("2026-04-11"))
				.andExpect(jsonPath("$.ratingTrend[10].pog").value(true))
				.andExpect(jsonPath("$.mostChampions[0].avgRating").value(6.0));
	}

	@Test
	@DisplayName("상대의 15분 기록이 없으면 차이는 null이며 실제 0인 기록은 계산에 사용합니다.")
	void missingOpponentMetricsAreNotZeroFilled() throws Exception {
		Sample unknownEnemy = sample();
		unknownEnemy.gold15 = null;
		unknownEnemy.cs15 = null;
		unknownEnemy.xp15 = null;
		addSet(player, "26.08", championA, LocalDate.of(2026, 4, 1), sample(), unknownEnemy);
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keyMetrics[3].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[4].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[5].value").value(nullValue()));

		Sample zeroEnemy = sample();
		zeroEnemy.gold15 = 0;
		zeroEnemy.cs15 = 0;
		zeroEnemy.xp15 = 0;
		addSet(player, "26.08", championA, LocalDate.of(2026, 4, 2), sample(), zeroEnemy);
		request(overview())
				.andExpect(jsonPath("$.keyMetrics[3].value").value(5000.0))
				.andExpect(jsonPath("$.keyMetrics[4].value").value(100.0))
				.andExpect(jsonPath("$.keyMetrics[5].value").value(6000.0));
	}

	@Test
	@DisplayName("이적 전 상대 팀과 팀원 전체 분모를 유지하며 분당 골드는 획득 골드를 사용합니다.")
	void usesHistoricalTeamAndFullTeamDenominators() throws Exception {
		addSet(player, "26.08", championA, LocalDate.of(2026, 4, 1), sample(), null);
		player.changeTeam(opposingTeam);
		request(overview().param("championId", championA.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recentSets[0].opponent.teamId").value(opposingTeam.getId()))
				.andExpect(jsonPath("$.recentSets[0].opponent.code").value("POB"))
				.andExpect(jsonPath("$.keyMetrics[1].code").value("DMG_PCT"))
				.andExpect(jsonPath("$.keyMetrics[1].value").value(0.25))
				.andExpect(jsonPath("$.keyMetrics[2].code").value("KP"))
				.andExpect(jsonPath("$.keyMetrics[2].value").value(0.5))
				.andExpect(jsonPath("$.keyMetrics[6].code").value("GPM"))
				.andExpect(jsonPath("$.keyMetrics[6].value").value(300.0))
				.andExpect(jsonPath("$.keyMetrics[7].value").value(10.0))
				.andExpect(jsonPath("$.keyMetrics[8].value").value(2.0))
				.andExpect(jsonPath("$.keyMetrics[9].value").value(1.0));
	}

	@Test
	@DisplayName("경기 시간이 0이면 분당 지표를 null로 두고 나머지 기록은 유지합니다.")
	void zeroDurationDoesNotProduceInfiniteMetrics() throws Exception {
		Sample values = sample();
		values.seconds = 0;
		addSet(player, "26.08", championA, LocalDate.of(2026, 4, 1), values, null);
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keyMetrics[0].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[6].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[7].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[8].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[9].value").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[1].value").value(0.25))
				.andExpect(jsonPath("$.ratingTrend.length()").value(1));
	}

	@Test
	@DisplayName("비교 평균은 같은 포지션의 열 세트 이상 선수별 평균이며 다른 연도와 표본 부족을 제외합니다.")
	void comparesEligiblePlayerAveragesWithoutSetCountWeighting() throws Exception {
		Player peer = player("PO_Peer", Position.MID);
		Player shortSample = player("PO_Short", Position.MID);
		Player otherPosition = player("PO_Other", Position.JUNGLE);
		Sample own = sample();
		own.seconds = 600; own.damage = 1000; own.deaths = 1;
		Sample peerValues = sample();
		peerValues.seconds = 600; peerValues.damage = 3000; peerValues.deaths = 3;
		Sample extreme = sample();
		extreme.seconds = 600; extreme.damage = 100000;
		records(player, 10, own);
		records(peer, 20, peerValues);
		records(shortSample, 9, extreme);
		records(otherPosition, 10, extreme);
		addSet(player, "26.08", championA, LocalDate.of(2025, 12, 31), extreme, null);
		addSet(player, "26.08", championA, LocalDate.of(2027, 1, 1), extreme, null);
		season(player, 10, 80);
		season(peer, 20, 90);

		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.compareGroup.playerCount").value(2))
				.andExpect(jsonPath("$.keyMetrics[0].value").value(100.0))
				.andExpect(jsonPath("$.keyMetrics[0].positionAvg").value(200.0))
				.andExpect(jsonPath("$.keyMetrics[0].topPercent").value(100.0))
				.andExpect(jsonPath("$.keyMetrics[0].grade").value("C"))
				.andExpect(jsonPath("$.keyMetrics[9].value").value(1.0))
				.andExpect(jsonPath("$.keyMetrics[9].positionAvg").value(2.0))
				.andExpect(jsonPath("$.keyMetrics[9].topPercent").value(0.0))
				.andExpect(jsonPath("$.keyMetrics[9].grade").value("A+"))
				.andExpect(jsonPath("$.ratingTrend.length()").value(10));
	}

	@Test
	@DisplayName("같은 지표 값은 같은 중간 순위 백분위를 받습니다.")
	void percentileTiesShareTheMidrank() throws Exception {
		Player tie = player("PO_Tie", Position.MID);
		Player stronger = player("PO_Strong", Position.MID);
		Sample first = sample();
		first.seconds = 600; first.damage = 1000;
		Sample high = sample();
		high.seconds = 600; high.damage = 3000;
		records(player, 10, first);
		records(tie, 10, first);
		records(stronger, 10, high);
		season(player, 10, 80);
		season(tie, 10, 80);
		season(stronger, 10, 90);
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.compareGroup.playerCount").value(3))
				.andExpect(jsonPath("$.keyMetrics[0].positionAvg", closeTo(166.6667, 0.1)))
				.andExpect(jsonPath("$.keyMetrics[0].topPercent").value(75.0))
				.andExpect(jsonPath("$.keyMetrics[0].grade").value("C+"));
	}

	@Test
	@DisplayName("비교 대상이 한 명뿐이면 지표 평균은 보여주고 백분위와 지표 등급은 비워 둡니다.")
	void singletonComparisonHasNoInventedPercentile() throws Exception {
		records(player, 10, sample());
		season(player, 10, 95);
		request(overview())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keyMetrics[0].positionAvg").value(500.0))
				.andExpect(jsonPath("$.keyMetrics[0].topPercent").value(nullValue()))
				.andExpect(jsonPath("$.keyMetrics[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.overall.grade").value("A+"));
	}

	@Test
	@DisplayName("필터를 걸어도 비교 자격은 전체 시즌 출전 수로 정하고 같은 필터의 평균을 비교합니다.")
	void filteredComparisonsKeepSeasonEligibility() throws Exception {
		Player peer = player("PO_FilterPeer", Position.MID);
		Sample own = sample(); own.seconds = 600; own.damage = 1000;
		Sample other = sample(); other.seconds = 600; other.damage = 3000;
		for (int i = 0; i < 10; i++) {
			Champion champion = i == 0 ? championA : championB;
			addSet(player, "26.08", champion, LocalDate.of(2026, 4, 1).plusDays(i), own, null);
			addSet(peer, "26.08", champion, LocalDate.of(2026, 4, 1).plusDays(i), other, null);
		}
		request(overview().param("championId", championA.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.compareGroup.playerCount").value(2))
				.andExpect(jsonPath("$.keyMetrics[0].value").value(100.0))
				.andExpect(jsonPath("$.keyMetrics[0].positionAvg").value(200.0))
				.andExpect(jsonPath("$.keyMetrics[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.ratingTrend.length()").value(1));
	}

	@ParameterizedTest
	@CsvSource({"championId,0", "championId,-1", "championId,abc", "patch,1234567890123456"})
	@DisplayName("잘못된 필터 값은 공통 입력 오류로 응답합니다.")
	void invalidFiltersReturnBadRequest(String key, String value) throws Exception {
		request(overview().param(key, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	@DisplayName("없는 선수와 잘못된 선수 번호를 구별합니다.")
	void rejectsUnknownAndInvalidPlayers() throws Exception {
		request(get("/api/v1/players/{id}/overview", Long.MAX_VALUE))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_001"));
		request(get("/api/v1/players/{id}/overview", 0))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	private Player player(String nickname, Position position) {
		return persist(Player.builder().team(originalTeam).nickname(nickname).position(position).build());
	}

	private void records(Player owner, int count, Sample values) {
		for (int i = 0; i < count; i++) addSet(owner, "26.08", championA,
				LocalDate.of(2026, 4, 1).plusDays(i), values, null);
	}

	private void addSet(Player owner, String patch, Champion champion, LocalDate date,
			Sample values, Sample enemyValues) {
		LckMatch match = persist(LckMatch.builder().team1(originalTeam).team2(opposingTeam)
				.stage(stage).sets(3).matchTime(date.atTime(17, 0)).build());
		MatchSet set = persist(MatchSet.builder().match(match).setNumber(1)
				.gameId("PO_GAME_" + ++gameNumber).version(patch)
				.playTime(LocalTime.ofSecondOfDay(values.seconds)).build());
		TeamSet ownTeamSet = persist(TeamSet.builder().team(originalTeam).matchSet(set)
				.side(Side.BLUE).victory(true).build());
		persist(setRecord(owner, ownTeamSet, champion, owner.getPosition(), values));
		// 다른 챔피언인 팀원의 기록도 킬 및 피해량의 분모에 포함되어야 합니다.
		persist(PlayerSet.builder().player(teammate).teamSet(ownTeamSet).champion(championB)
				.position(owner.getPosition() == Position.TOP ? Position.JUNGLE : Position.TOP)
				.kills(8).damageToChampions(30000).build());
		if (enemyValues != null) {
			TeamSet enemyTeamSet = persist(TeamSet.builder().team(opposingTeam).matchSet(set)
					.side(Side.RED).victory(false).build());
			persist(setRecord(enemy, enemyTeamSet, championB, owner.getPosition(), enemyValues));
		}
	}

	private PlayerSet setRecord(Player owner, TeamSet teamSet, Champion champion, Position position, Sample values) {
		return PlayerSet.builder().player(owner).teamSet(teamSet).champion(champion).position(position)
				.kills(values.kills).deaths(values.deaths).assists(values.assists)
				.damageToChampions(values.damage).earnedGolds(values.earnedGold).totalGolds(values.totalGold)
				.minionKills(values.cs).visionPoints(values.vision)
				.goldAt15(values.gold15).csAt15(values.cs15).xpAt15(values.xp15)
				.totalPoints(values.points).pog(values.pog).build();
	}

	private void season(Player owner, int sets, Integer score) {
		persist(PlayerSeason.builder().player(owner).sets(sets).totalPoints(score).rankInPosition(1)
				.combatPoints(80).damagePoints(70).laningPoints(60).growthPoints(50)
				.wardPoints(40).survivalPoints(30)
				.calculatedAt(LocalDateTime.of(2026, 11, 19, 12, 0)).build());
	}

	private Sample sample() {
		return new Sample();
	}

	private static class Sample {
		int seconds = 1200;
		int kills = 2;
		int deaths = 2;
		int assists = 3;
		int damage = 10000;
		int earnedGold = 6000;
		int totalGold = 20000;
		int cs = 200;
		int vision = 40;
		Integer gold15 = 5000;
		Integer cs15 = 100;
		Integer xp15 = 6000;
		Integer points = 50;
		boolean pog;
	}

	private MockHttpServletRequestBuilder overview() {
		return get("/api/v1/players/{id}/overview", player.getId());
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
