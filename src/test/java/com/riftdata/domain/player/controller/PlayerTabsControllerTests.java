package com.riftdata.domain.player.controller;

import static org.hamcrest.Matchers.contains;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 영역 탭(API-16)과 챔피언 탭(API-17).
 *
 * <p>대상 선수 PT_Mid(TBA, MID)와 라인 상대 PT_Enemy(TBB, MID)가 30분짜리 세트 10개에서 맞붙는다.
 * 앞 6세트는 챔피언 A(4승 2패), 뒤 4세트는 챔피언 B(1승 3패).
 * 세트마다 PT_Mid 3/1/4, 피해 18,000, CS 270, 시야 45, 와드 12·제거 6·제어 3, 15분 골드 6,000.
 * PT_Enemy 1/3/2, 피해 12,000, CS 240, 시야 30, 와드 8·제거 4·제어 1, 15분 골드 5,500.
 * 같은 팀 PT_Top이 세트마다 5킬 → TBA 팀 킬은 세트당 8.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerTabsControllerTests {

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;

	private Team own;
	private Team opposing;
	private Player player;
	private Player teammate;
	private Player enemy;
	private Champion championA;
	private Champion championB;
	private Stage stage;
	private int gameNumber;

	@BeforeEach
	void setUp() {
		own = persist(Team.builder().code("TBA").build());
		opposing = persist(Team.builder().code("TBB").build());
		player = persist(Player.builder().team(own).nickname("PT_Mid").position(Position.MID).build());
		teammate = persist(Player.builder().team(own).nickname("PT_Top").position(Position.TOP).build());
		enemy = persist(Player.builder().team(opposing).nickname("PT_Enemy").position(Position.MID).build());
		championA = persist(Champion.builder().name("PT_ChampA").build());
		championB = persist(Champion.builder().name("PT_ChampB").build());
		stage = persist(Stage.builder().name("PT Stage").stageOrder(94)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).regular(false).build());
		for (int i = 0; i < 10; i++) {
			boolean championAGame = i < 6;
			boolean win = championAGame ? i < 4 : i == 6;
			addSet(LocalDate.of(2026, 4, 1).plusDays(i), championAGame ? championA : championB, win,
					championAGame ? 70 : 80, i < 5 ? "26.08" : "26.09");
		}
		persist(PlayerSeason.builder().player(player).sets(10).totalPoints(85).rankInPosition(1)
				.combatPoints(80).damagePoints(70).laningPoints(60).growthPoints(50)
				.wardPoints(40).survivalPoints(30)
				.calculatedAt(LocalDateTime.of(2026, 11, 19, 12, 0)).build());
	}

	@Test
	@DisplayName("전투 탭: 영역 카드 2개, 영역 지표의 값·포지션 평균·상위%, 세트 추이, 킬 관여 구성")
	void combatGroup() throws Exception {
		areas("combat")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.group").value("combat"))
				.andExpect(jsonPath("$.areaCards[*].area").value(contains("COMBAT", "DAMAGE")))
				.andExpect(jsonPath("$.areaCards[0].score").value(80.0))
				.andExpect(jsonPath("$.areaCards[0].grade").value("A-"))
				.andExpect(jsonPath("$.areaCards[0].topPercent").value(20.0))
				.andExpect(jsonPath("$.areaCards[0].metrics[*].code").value(contains("KDA", "KP", "FB_PCT", "KA15")))
				// KDA (30 + 40) ÷ 10 = 7.0, 상대 (10 + 20) ÷ 30 = 1.0 → 평균 4.0, 두 명 중 1위 → 상위 0%
				.andExpect(jsonPath("$.areaCards[0].metrics[0].value").value(7.0))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].positionAvg").value(4.0))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].topPercent").value(0.0))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].grade").value("A+"))
				// KP 70 ÷ 80
				.andExpect(jsonPath("$.areaCards[0].metrics[1].value").value(0.875))
				.andExpect(jsonPath("$.areaCards[1].metrics[*].code").value(contains("DPM", "DMG_PCT", "DMG_PER_GOLD")))
				.andExpect(jsonPath("$.areaCards[1].metrics[0].value").value(600.0))
				.andExpect(jsonPath("$.setSeries.length()").value(10))
				.andExpect(jsonPath("$.setSeries[0].dpm").value(600.0))
				.andExpect(jsonPath("$.setSeries[0].kills").value(3))
				.andExpect(jsonPath("$.setSeries[0].assists").value(4))
				.andExpect(jsonPath("$.extra.killParticipation.kills").value(30))
				.andExpect(jsonPath("$.extra.killParticipation.assists").value(40))
				.andExpect(jsonPath("$.extra.killParticipation.teamKills").value(80))
				.andExpect(jsonPath("$.extra.killParticipation.notInvolved").value(10))
				.andExpect(jsonPath("$.extra.championDamage.length()").value(2));
	}

	@Test
	@DisplayName("라인전 탭: 세트별 15분 골드 차와 분당 CS, 라인 상대별 15분 지표")
	void laneGroup() throws Exception {
		areas("lane")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.areaCards[*].area").value(contains("LANE", "GROWTH")))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].code").value("GD15"))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].value").value(500.0))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].positionAvg").value(0.0))
				.andExpect(jsonPath("$.setSeries[0].goldDiff15").value(500.0))
				.andExpect(jsonPath("$.setSeries[0].csPerMin").value(9.0))
				.andExpect(jsonPath("$.extra.laneMatchups.length()").value(1))
				.andExpect(jsonPath("$.extra.laneMatchups[0].opponentPlayerId").value(enemy.getId().intValue()))
				.andExpect(jsonPath("$.extra.laneMatchups[0].name").value("PT_Enemy"))
				.andExpect(jsonPath("$.extra.laneMatchups[0].teamCode").value("TBB"))
				.andExpect(jsonPath("$.extra.laneMatchups[0].sets").value(10))
				.andExpect(jsonPath("$.extra.laneMatchups[0].wins").value(5))
				.andExpect(jsonPath("$.extra.laneMatchups[0].losses").value(5))
				.andExpect(jsonPath("$.extra.laneMatchups[0].goldDiff15").value(500.0))
				// 70점 6세트(7.6), 80점 4세트(8.4) → 7.92 → 7.9
				.andExpect(jsonPath("$.extra.laneMatchups[0].avgRating").value(7.9));
	}

	@Test
	@DisplayName("생존·시야 탭: 생존 → 시야 순서, 와드 요약은 세트당 평균과 포지션 평균")
	void visionGroup() throws Exception {
		areas("vision")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.areaCards[*].area").value(contains("SURVIVAL", "VISION")))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].code").value("D10"))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].direction").value(-1))
				.andExpect(jsonPath("$.setSeries[0].deaths").value(1))
				.andExpect(jsonPath("$.setSeries[0].visionScorePerMin").value(1.5))
				.andExpect(jsonPath("$.extra.wardSummary[*].key").value(contains(
						"WARDS_PLACED", "WARDS_REMOVED", "CONTROL_WARDS_BOUGHT", "VISION_SCORE")))
				.andExpect(jsonPath("$.extra.wardSummary[0].value").value(12.0))
				.andExpect(jsonPath("$.extra.wardSummary[0].positionAvg").value(10.0))
				.andExpect(jsonPath("$.extra.wardSummary[3].value").value(45.0))
				.andExpect(jsonPath("$.extra.wardSummary[3].positionAvg").value(37.5));
	}

	@Test
	@DisplayName("필터를 걸면 시즌 점수·등급·상위%는 null이고 값은 조건에 맞게 계산한다")
	void filterHidesSeasonGrades() throws Exception {
		request(get("/api/v1/players/{id}/areas", player.getId()).param("group", "combat").param("patch", "26.08"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.areaCards[0].score").value(nullValue()))
				.andExpect(jsonPath("$.areaCards[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].value").value(7.0))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].topPercent").value(nullValue()))
				.andExpect(jsonPath("$.areaCards[0].metrics[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.setSeries.length()").value(5));
	}

	@Test
	@DisplayName("챔피언 탭: 챔피언 폭과 챔피언별 성적, 5세트 미만은 참고용")
	void championsTab() throws Exception {
		request(get("/api/v1/players/{id}/champions", player.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.championPool.count").value(2))
				.andExpect(jsonPath("$.championPool.topShare").value(0.6))
				// 비교 집단: PT_Mid 2개, PT_Enemy 1개
				.andExpect(jsonPath("$.championPool.positionAvgCount").value(1.5))
				.andExpect(jsonPath("$.champions[*].name").value(contains("PT_ChampA", "PT_ChampB")))
				.andExpect(jsonPath("$.champions[0].sets").value(6))
				.andExpect(jsonPath("$.champions[0].wins").value(4))
				.andExpect(jsonPath("$.champions[0].losses").value(2))
				.andExpect(jsonPath("$.champions[0].winRate").value(0.667))
				.andExpect(jsonPath("$.champions[0].kills").value(18))
				.andExpect(jsonPath("$.champions[0].kda").value(7.0))
				.andExpect(jsonPath("$.champions[0].dpm").value(600.0))
				.andExpect(jsonPath("$.champions[0].goldDiff15").value(500.0))
				.andExpect(jsonPath("$.champions[0].avgRating").value(7.6))
				.andExpect(jsonPath("$.champions[0].lowSample").value(false))
				.andExpect(jsonPath("$.champions[0].setRatings.length()").value(6))
				.andExpect(jsonPath("$.champions[1].sets").value(4))
				.andExpect(jsonPath("$.champions[1].lowSample").value(true));
		request(get("/api/v1/players/{id}/champions", player.getId()).param("sort", "avgRating"))
				.andExpect(jsonPath("$.champions[*].name").value(contains("PT_ChampB", "PT_ChampA")));
		request(get("/api/v1/players/{id}/champions", player.getId()).param("sort", "winRate"))
				.andExpect(jsonPath("$.champions[*].name").value(contains("PT_ChampA", "PT_ChampB")));
	}

	@Test
	@DisplayName("기록이 없는 선수의 챔피언 탭은 빈 목록과 null 비중")
	void championsTabWithoutRecords() throws Exception {
		request(get("/api/v1/players/{id}/champions", teammate.getId()).param("patch", "99.99"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.championPool.count").value(0))
				.andExpect(jsonPath("$.championPool.topShare").value(nullValue()))
				.andExpect(jsonPath("$.champions.length()").value(0));
	}

	@ParameterizedTest
	@CsvSource({"areas,group,", "areas,group,laning", "champions,sort,kda", "champions,championId,0"})
	@DisplayName("잘못된 group·sort·필터는 400 COMMON_001")
	void rejectsInvalidParameters(String tab, String key, String value) throws Exception {
		var request = get("/api/v1/players/{id}/" + tab, player.getId());
		if (value != null) request.param(key, value);
		if (tab.equals("areas") && !key.equals("group")) request.param("group", "combat");
		request(request)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	@DisplayName("없는 선수는 404 PLAYER_001")
	void unknownPlayer() throws Exception {
		request(get("/api/v1/players/{id}/areas", Long.MAX_VALUE).param("group", "lane"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_001"));
		request(get("/api/v1/players/{id}/champions", Long.MAX_VALUE))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_001"));
	}

	private ResultActions areas(String group) throws Exception {
		return request(get("/api/v1/players/{id}/areas", player.getId()).param("group", group));
	}

	private ResultActions request(org.springframework.test.web.servlet.RequestBuilder builder) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(builder);
	}

	private void addSet(LocalDate date, Champion champion, boolean win, int points, String patch) {
		LckMatch match = persist(LckMatch.builder().team1(own).team2(opposing)
				.stage(stage).sets(3).matchTime(date.atTime(17, 0)).build());
		MatchSet set = persist(MatchSet.builder().match(match).setNumber(1)
				.gameId("PT_GAME_" + ++gameNumber).version(patch)
				.playTime(LocalTime.ofSecondOfDay(1800)).build());
		TeamSet ownSet = persist(TeamSet.builder().team(own).matchSet(set).side(Side.BLUE).victory(win).build());
		TeamSet enemySet = persist(TeamSet.builder().team(opposing).matchSet(set).side(Side.RED).victory(!win).build());
		persist(PlayerSet.builder().player(player).teamSet(ownSet).champion(champion).position(Position.MID)
				.kills(3).deaths(1).assists(4).damageToChampions(18_000).earnedGolds(9_000)
				.minionKills(270).visionPoints(45).wardsPlaced(12).wardsRemoved(6).controlWardsBought(3)
				.goldAt15(6_000).csAt15(130).xpAt15(7_000).totalPoints(points).build());
		persist(PlayerSet.builder().player(teammate).teamSet(ownSet).champion(championA).position(Position.TOP)
				.kills(5).build());
		persist(PlayerSet.builder().player(enemy).teamSet(enemySet).champion(championB).position(Position.MID)
				.kills(1).deaths(3).assists(2).damageToChampions(12_000).earnedGolds(8_000)
				.minionKills(240).visionPoints(30).wardsPlaced(8).wardsRemoved(4).controlWardsBought(1)
				.goldAt15(5_500).csAt15(120).xpAt15(6_800).totalPoints(50).build());
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}
}
