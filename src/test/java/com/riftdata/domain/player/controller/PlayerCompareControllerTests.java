package com.riftdata.domain.player.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
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
 * 선수 비교 (API-18).
 *
 * <p>PC_Mid1(CPA, MID)과 PC_Mid2(CPB, MID)가 30분짜리 세트 10개에서 라인 상대로 맞붙는다. CPA가 앞 6세트 승리.
 * 세트마다 PC_Mid1 3/1/4, 15분 골드 6,000 / PC_Mid2 1/3/2, 15분 골드 5,500.
 * PC_Top(CPB, TOP)도 같은 세트에 나와 PC_Mid1과 같은 세트, 다른 포지션이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerCompareControllerTests {

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;

	private Player mid1;
	private Player mid2;
	private Player top;

	@BeforeEach
	void setUp() {
		Team teamA = persist(Team.builder().code("CPA").build());
		Team teamB = persist(Team.builder().code("CPB").build());
		mid1 = persist(Player.builder().team(teamA).nickname("PC_Mid1").position(Position.MID).build());
		mid2 = persist(Player.builder().team(teamB).nickname("PC_Mid2").position(Position.MID).build());
		top = persist(Player.builder().team(teamB).nickname("PC_Top").position(Position.TOP).build());
		Champion champA = persist(Champion.builder().name("PC_ChampA").build());
		Champion champB = persist(Champion.builder().name("PC_ChampB").build());
		Stage stage = persist(Stage.builder().name("PC Stage").stageOrder(93)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).regular(false).build());
		for (int i = 0; i < 10; i++) {
			boolean aWins = i < 6;
			LckMatch match = persist(LckMatch.builder().team1(teamA).team2(teamB).stage(stage).sets(3)
					.matchTime(LocalDate.of(2026, 4, 1).plusDays(i).atTime(17, 0)).build());
			MatchSet set = persist(MatchSet.builder().match(match).setNumber(1).gameId("PC_GAME_" + i)
					.version("26.08").playTime(LocalTime.ofSecondOfDay(1800)).build());
			TeamSet a = persist(TeamSet.builder().team(teamA).matchSet(set).side(Side.BLUE).victory(aWins).build());
			TeamSet b = persist(TeamSet.builder().team(teamB).matchSet(set).side(Side.RED).victory(!aWins).build());
			persist(PlayerSet.builder().player(mid1).teamSet(a).champion(champA).position(Position.MID)
					.kills(3).deaths(1).assists(4).damageToChampions(18_000).goldAt15(6_000).totalPoints(70).build());
			persist(PlayerSet.builder().player(mid2).teamSet(b).champion(champB).position(Position.MID)
					.kills(1).deaths(3).assists(2).damageToChampions(12_000).goldAt15(5_500).totalPoints(50).build());
			persist(PlayerSet.builder().player(top).teamSet(b).champion(champA).position(Position.TOP)
					.kills(2).deaths(2).assists(2).goldAt15(5_800).build());
		}
		season(mid1, 85);
		season(mid2, 70);
	}

	@Test
	@DisplayName("같은 포지션: 점수·등급, 지표 값과 더 좋은 쪽, 맞대결 기록")
	void comparesSamePosition() throws Exception {
		compare(mid1, mid2)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.samePosition").value(true))
				.andExpect(jsonPath("$.players[*].name").value(contains("PC_Mid1", "PC_Mid2")))
				.andExpect(jsonPath("$.players[0].teamCode").value("CPA"))
				.andExpect(jsonPath("$.players[0].sets").value(10))
				.andExpect(jsonPath("$.players[0].winRate").value(0.6))
				.andExpect(jsonPath("$.players[0].overallScore").value(85.0))
				.andExpect(jsonPath("$.players[0].grade").value("A"))
				.andExpect(jsonPath("$.players[0].areas.length()").value(6))
				.andExpect(jsonPath("$.players[0].areas[0].area").value("COMBAT"))
				.andExpect(jsonPath("$.players[0].areas[0].score").value(80.0))
				.andExpect(jsonPath("$.players[0].areas[0].grade").value("A-"))
				.andExpect(jsonPath("$.players[1].grade").value("B+"))
				.andExpect(jsonPath("$.metrics.length()").value(11))
				.andExpect(jsonPath("$.metrics[0].code").value("KDA"))
				.andExpect(jsonPath("$.metrics[0].values").value(contains(7.0, 1.0)))
				.andExpect(jsonPath("$.metrics[0].better").value(0))
				// D/10은 낮을수록 좋다: 1/30분 → 0.33, 3/30분 → 1.0
				.andExpect(jsonPath("$.metrics[10].code").value("D10"))
				.andExpect(jsonPath("$.metrics[10].direction").value(-1))
				.andExpect(jsonPath("$.metrics[10].values").value(contains(0.33, 1.0)))
				.andExpect(jsonPath("$.metrics[10].better").value(0))
				.andExpect(jsonPath("$.headToHead.wins").value(contains(6, 4)))
				.andExpect(jsonPath("$.headToHead.sets.length()").value(10))
				.andExpect(jsonPath("$.headToHead.sets[0].date").value("2026-04-01"))
				.andExpect(jsonPath("$.headToHead.sets[0].champions").value(contains("PC_ChampA", "PC_ChampB")))
				.andExpect(jsonPath("$.headToHead.sets[0].ratings").value(contains(7.6, 6.0)))
				.andExpect(jsonPath("$.headToHead.sets[0].goldDiff15").value(500.0))
				.andExpect(jsonPath("$.headToHead.sets[0].winnerPlayerId").value(mid1.getId().intValue()))
				.andExpect(jsonPath("$.headToHead.sets[9].winnerPlayerId").value(mid2.getId().intValue()));
	}

	@Test
	@DisplayName("포지션이 다르면 등급은 모두 null이고 맞대결 15분 골드 차도 비교하지 않는다")
	void differentPositionsHideGrades() throws Exception {
		compare(mid1, top)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.samePosition").value(false))
				.andExpect(jsonPath("$.players[0].overallScore").value(85.0))
				.andExpect(jsonPath("$.players[0].grade").value(nullValue()))
				.andExpect(jsonPath("$.players[0].areas[*].grade").value(everyItem(nullValue())))
				// PC_Top은 시즌 점수가 없다
				.andExpect(jsonPath("$.players[1].overallScore").value(nullValue()))
				.andExpect(jsonPath("$.headToHead.sets.length()").value(10))
				.andExpect(jsonPath("$.headToHead.sets[0].goldDiff15").value(nullValue()));
	}

	@Test
	@DisplayName("값이 같거나 한쪽이 없으면 better는 null")
	void betterIsNullWhenTiedOrMissing() throws Exception {
		// PC_Top의 DPM은 0 (피해량 0), KP는 팀 킬이 있어 계산된다. 15분 CS 차는 둘 다 기록이 없어 null
		compare(mid1, top)
				.andExpect(jsonPath("$.metrics[?(@.code == 'CSD15')].values[*]").value(everyItem(nullValue())))
				.andExpect(jsonPath("$.metrics[?(@.code == 'CSD15')].better").value(contains((Object) null)));
	}

	@ParameterizedTest
	@CsvSource({"p1only", "same", "text"})
	@DisplayName("p1·p2가 없거나 같거나 숫자가 아니면 400 COMMON_001")
	void rejectsInvalidParameters(String kind) throws Exception {
		var request = get("/api/v1/players/compare");
		switch (kind) {
			case "p1only" -> request.param("p1", mid1.getId().toString());
			case "same" -> request.param("p1", mid1.getId().toString()).param("p2", mid1.getId().toString());
			default -> request.param("p1", "abc").param("p2", mid2.getId().toString());
		}
		mockMvc.perform(request)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	@DisplayName("없는 선수는 404 PLAYER_001")
	void unknownPlayer() throws Exception {
		mockMvc.perform(get("/api/v1/players/compare").param("p1", mid1.getId().toString())
						.param("p2", String.valueOf(Long.MAX_VALUE)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PLAYER_001"));
	}

	private ResultActions compare(Player p1, Player p2) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get("/api/v1/players/compare")
				.param("p1", p1.getId().toString()).param("p2", p2.getId().toString()));
	}

	private void season(Player player, int score) {
		persist(PlayerSeason.builder().player(player).sets(10).totalPoints(score).rankInPosition(1)
				.combatPoints(80).damagePoints(70).laningPoints(60).growthPoints(50)
				.wardPoints(40).survivalPoints(30)
				.calculatedAt(LocalDateTime.of(2026, 11, 19, 12, 0)).build());
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}
}
