package com.riftdata.domain.champion;

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
import com.riftdata.domain.record.entity.Ban;
import com.riftdata.domain.record.entity.PlayerSet;
import com.riftdata.domain.record.entity.TeamSet;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챔피언 통계(API-19)와 챔피언을 잘 쓰는 선수(API-20).
 *
 * <p>패치 CS.TEST의 세트 4개. CSA가 1~3세트, CSB가 4세트 승리.
 * <ul>
 *   <li>CS_MidA(CSA, MID): 4세트 모두 CS_Azir, 3/1/4, 70점(평점 7.6)</li>
 *   <li>CS_TopA(CSA, TOP): 4세트 모두 CS_Jax</li>
 *   <li>CS_MidB(CSB, MID): 1·2세트 CS_Ahri, 3·4세트 CS_Rare</li>
 *   <li>밴: CSA가 4세트 모두 CS_Yone, CSB가 1·2세트에 CS_Lee</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChampionControllerTests {

	private static final String PATCH = "CS.TEST";

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;

	private Champion azir;
	private Champion ahri;
	private Player midA;

	@BeforeEach
	void setUp() {
		Team teamA = persist(Team.builder().code("CSA").build());
		Team teamB = persist(Team.builder().code("CSB").build());
		midA = persist(Player.builder().team(teamA).nickname("CS_MidA").position(Position.MID).build());
		Player topA = persist(Player.builder().team(teamA).nickname("CS_TopA").position(Position.TOP).build());
		Player midB = persist(Player.builder().team(teamB).nickname("CS_MidB").position(Position.MID).build());
		azir = persist(Champion.builder().name("CS_Azir").build());
		ahri = persist(Champion.builder().name("CS_Ahri").build());
		Champion rare = persist(Champion.builder().name("CS_Rare").build());
		Champion jax = persist(Champion.builder().name("CS_Jax").build());
		Champion yone = persist(Champion.builder().name("CS_Yone").build());
		Champion lee = persist(Champion.builder().name("CS_Lee").build());
		Stage stage = persist(Stage.builder().name("CS Stage").stageOrder(92)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).regular(false).build());
		for (int i = 0; i < 4; i++) {
			boolean aWins = i < 3;
			LckMatch match = persist(LckMatch.builder().team1(teamA).team2(teamB).stage(stage).sets(3)
					.matchTime(LocalDate.of(2026, 5, 1).plusDays(i).atTime(17, 0)).build());
			MatchSet set = persist(MatchSet.builder().match(match).setNumber(1).gameId("CS_GAME_" + i)
					.version(PATCH).playTime(LocalTime.ofSecondOfDay(1800)).build());
			TeamSet a = persist(TeamSet.builder().team(teamA).matchSet(set).side(Side.BLUE).victory(aWins).build());
			TeamSet b = persist(TeamSet.builder().team(teamB).matchSet(set).side(Side.RED).victory(!aWins).build());
			persist(PlayerSet.builder().player(midA).teamSet(a).champion(azir).position(Position.MID)
					.kills(3).deaths(1).assists(4).totalPoints(70).build());
			persist(PlayerSet.builder().player(topA).teamSet(a).champion(jax).position(Position.TOP)
					.kills(1).deaths(1).assists(1).totalPoints(50).build());
			persist(PlayerSet.builder().player(midB).teamSet(b).champion(i < 2 ? ahri : rare).position(Position.MID)
					.kills(2).deaths(2).assists(2).totalPoints(60).build());
			persist(Ban.builder().teamSet(a).banOrder(1).champion(yone).build());
			if (i < 2) {
				persist(Ban.builder().teamSet(b).banOrder(1).champion(lee).build());
			}
		}
	}

	@Test
	@DisplayName("챔피언 통계: 픽·밴·픽밴률·승률·KDA·평점·주 포지션·최다 사용 선수, 픽밴률 → 픽 수 → 이름 순")
	void championStats() throws Exception {
		request(get("/api/v1/champions").param("patch", PATCH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalSets").value(4))
				.andExpect(jsonPath("$.champions[*].name").value(contains(
						"CS_Azir", "CS_Jax", "CS_Yone", "CS_Ahri", "CS_Rare", "CS_Lee")))
				.andExpect(jsonPath("$.champions[0].mainPosition").value("MID"))
				.andExpect(jsonPath("$.champions[0].picks").value(4))
				.andExpect(jsonPath("$.champions[0].bans").value(0))
				.andExpect(jsonPath("$.champions[0].pickBanRate").value(1.0))
				.andExpect(jsonPath("$.champions[0].winRate").value(0.75))
				.andExpect(jsonPath("$.champions[0].kda").value(7.0))
				.andExpect(jsonPath("$.champions[0].avgRating").value(7.6))
				.andExpect(jsonPath("$.champions[0].topPlayer.playerId").value(midA.getId().intValue()))
				.andExpect(jsonPath("$.champions[0].topPlayer.name").value("CS_MidA"))
				.andExpect(jsonPath("$.champions[0].topPlayer.sets").value(4))
				.andExpect(jsonPath("$.champions[1].mainPosition").value("TOP"))
				// 밴만 된 챔피언: 픽 관련 값은 null
				.andExpect(jsonPath("$.champions[2].picks").value(0))
				.andExpect(jsonPath("$.champions[2].bans").value(4))
				.andExpect(jsonPath("$.champions[2].pickBanRate").value(1.0))
				.andExpect(jsonPath("$.champions[2].mainPosition").value(nullValue()))
				.andExpect(jsonPath("$.champions[2].winRate").value(nullValue()))
				.andExpect(jsonPath("$.champions[2].topPlayer").value(nullValue()))
				.andExpect(jsonPath("$.champions[3].pickBanRate").value(0.5))
				.andExpect(jsonPath("$.champions[3].winRate").value(0.0))
				.andExpect(jsonPath("$.champions[4].winRate").value(0.5))
				.andExpect(jsonPath("$.champions[5].bans").value(2));
	}

	@Test
	@DisplayName("포지션 필터는 그 포지션에서 픽된 챔피언만, 밴만 된 챔피언은 빠진다")
	void positionFilter() throws Exception {
		request(get("/api/v1/champions").param("patch", PATCH).param("position", "MID"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalSets").value(4))
				.andExpect(jsonPath("$.champions[*].name").value(contains("CS_Azir", "CS_Ahri", "CS_Rare")));
	}

	@Test
	@DisplayName("없는 패치는 세트 0, 빈 목록")
	void unknownPatch() throws Exception {
		request(get("/api/v1/champions").param("patch", "00.00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalSets").value(0))
				.andExpect(jsonPath("$.champions.length()").value(0));
	}

	@Test
	@DisplayName("챔피언을 잘 쓰는 선수: 3세트 이상만")
	void championPlayers() throws Exception {
		request(get("/api/v1/champions/{id}/players", azir.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].playerId").value(midA.getId().intValue()))
				.andExpect(jsonPath("$[0].name").value("CS_MidA"))
				.andExpect(jsonPath("$[0].teamCode").value("CSA"))
				.andExpect(jsonPath("$[0].sets").value(4))
				.andExpect(jsonPath("$[0].winRate").value(0.75))
				.andExpect(jsonPath("$[0].kda").value(7.0))
				.andExpect(jsonPath("$[0].avgRating").value(7.6));
		// CS_Ahri는 CS_MidB가 2세트만 써서 빈 목록
		request(get("/api/v1/champions/{id}/players", ahri.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@ParameterizedTest
	@CsvSource({"position,JGL", "patch,1234567890123456"})
	@DisplayName("잘못된 position·patch는 400 COMMON_001")
	void rejectsInvalidFilters(String key, String value) throws Exception {
		request(get("/api/v1/champions").param(key, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	@DisplayName("없는 챔피언은 404 CHAMPION_001, 잘못된 ID는 400")
	void unknownChampion() throws Exception {
		request(get("/api/v1/champions/{id}/players", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CHAMPION_001"));
		request(get("/api/v1/champions/{id}/players", 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	private ResultActions request(RequestBuilder builder) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(builder);
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}
}
