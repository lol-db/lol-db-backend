package com.riftdata.domain.match.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.global.exception.ErrorCode;
import com.riftdata.support.MatchFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 세트 상세 API (실제 MySQL, 끝나면 롤백). 값은 {@link MatchFixture} 주석 참고 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SetDetailControllerTests {

	private static final String URL = "/api/v1/matches/{matchId}/sets/{setNo}";

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;

	MatchFixture.Data data;

	@BeforeEach
	void setUp() {
		data = MatchFixture.create(em);
	}

	@Test
	@DisplayName("세트 정보와 블루 팀부터 밴픽·오브젝트·스코어보드를 준다")
	void setDetail() throws Exception {
		Long a = data.teamA().getId();
		mockMvc.perform(get(URL, data.finished().getId(), 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.matchId").value(data.finished().getId()))
				.andExpect(jsonPath("$.setNo").value(1))
				.andExpect(jsonPath("$.sourceGameId").value("ZZ-GAME-1"))
				.andExpect(jsonPath("$.patch").value("26.08"))
				.andExpect(jsonPath("$.durationSec").value(1800))
				.andExpect(jsonPath("$.winnerTeamId").value(a))
				.andExpect(jsonPath("$.teams.length()").value(2))
				// 1세트는 ZZA가 블루로 승리
				.andExpect(jsonPath("$.teams[0].code").value("ZZA"))
				.andExpect(jsonPath("$.teams[0].side").value("BLUE"))
				.andExpect(jsonPath("$.teams[0].win").value(true))
				.andExpect(jsonPath("$.teams[0].bans.length()").value(2))
				.andExpect(jsonPath("$.teams[0].bans[0].order").value(1))
				.andExpect(jsonPath("$.teams[0].bans[0].name").value("ZZBan0"))
				.andExpect(jsonPath("$.teams[0].picks.length()").value(5))
				.andExpect(jsonPath("$.teams[0].picks[0].name").value("ZZChamp0"))
				.andExpect(jsonPath("$.teams[0].picks[0].position").value("TOP"))
				// 오브젝트: 킬·골드는 선수 5명 합
				.andExpect(jsonPath("$.teams[0].objectives.kills").value(10))
				.andExpect(jsonPath("$.teams[0].objectives.gold").value(55000))
				.andExpect(jsonPath("$.teams[0].objectives.towers").value(9))
				.andExpect(jsonPath("$.teams[0].objectives.dragons").value(3))
				.andExpect(jsonPath("$.teams[0].objectives.voidGrubs").value(4))
				.andExpect(jsonPath("$.teams[0].objectives.barons").value(1))
				.andExpect(jsonPath("$.teams[0].objectives.inhibitors").value(2))
				// 스코어보드: 포지션 순, 미드가 세 번째
				.andExpect(jsonPath("$.teams[0].players.length()").value(5))
				.andExpect(jsonPath("$.teams[0].players[0].position").value("TOP"))
				.andExpect(jsonPath("$.teams[0].players[2].name").value("ZZA_MID"))
				.andExpect(jsonPath("$.teams[0].players[2].champion.name").value("ZZChamp2"))
				.andExpect(jsonPath("$.teams[0].players[2].kills").value(2))
				.andExpect(jsonPath("$.teams[0].players[2].deaths").value(1))
				.andExpect(jsonPath("$.teams[0].players[2].assists").value(3))
				.andExpect(jsonPath("$.teams[0].players[2].cs").value(210))
				.andExpect(jsonPath("$.teams[0].players[2].gold").value(11000))
				.andExpect(jsonPath("$.teams[0].players[2].dpm").value(500)) // 15,000 ÷ 30분
				.andExpect(jsonPath("$.teams[0].players[2].damageShare").value(0.2))
				.andExpect(jsonPath("$.teams[0].players[2].visionScore").value(30))
				.andExpect(jsonPath("$.teams[0].players[2].rating").value(9.0))
				.andExpect(jsonPath("$.teams[0].players[2].pog").value(true))
				.andExpect(jsonPath("$.teams[0].players[0].pog").value(false))
				// 진 팀 (레드)
				.andExpect(jsonPath("$.teams[1].code").value("ZZB"))
				.andExpect(jsonPath("$.teams[1].side").value("RED"))
				.andExpect(jsonPath("$.teams[1].win").value(false))
				.andExpect(jsonPath("$.teams[1].bans[1].name").value("ZZBan3"))
				.andExpect(jsonPath("$.teams[1].objectives.towers").value(3))
				// 골드 차: 블루가 선수당 20씩 앞섬, 25분 기록이 없어 3개 지점
				.andExpect(jsonPath("$.goldDiff.length()").value(3))
				.andExpect(jsonPath("$.goldDiff[0].minute").value(10))
				.andExpect(jsonPath("$.goldDiff[0].diff").value(100))
				.andExpect(jsonPath("$.goldDiff[2].minute").value(20));
	}

	@Test
	@DisplayName("경기의 팀 순서와 상관없이 블루 팀이 먼저 나온다")
	void blueTeamComesFirst() throws Exception {
		// 2세트는 경기의 팀 B(ZZB)가 블루로 이긴 세트이고, 밴이 없다
		mockMvc.perform(get(URL, data.finished().getId(), 2))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.winnerTeamId").value(data.teamB().getId()))
				.andExpect(jsonPath("$.teams[0].code").value("ZZB"))
				.andExpect(jsonPath("$.teams[0].side").value("BLUE"))
				.andExpect(jsonPath("$.teams[1].code").value("ZZA"))
				.andExpect(jsonPath("$.teams[1].win").value(false))
				.andExpect(jsonPath("$.teams[0].bans.length()").value(0))
				.andExpect(jsonPath("$.goldDiff[0].diff").value(100));
	}

	@Test
	@DisplayName("경기는 있는데 세트가 없으면 404 SET_NOT_FOUND이다")
	void unknownSetIsNotFound() throws Exception {
		mockMvc.perform(get(URL, data.finished().getId(), 4))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.SET_NOT_FOUND.getCode()));
		// 예정 경기에는 세트가 아직 없다
		mockMvc.perform(get(URL, data.scheduled().getId(), 1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.SET_NOT_FOUND.getCode()));
	}

	@Test
	@DisplayName("없는 경기는 404 MATCH_NOT_FOUND이다")
	void unknownMatchIsNotFound() throws Exception {
		mockMvc.perform(get(URL, Long.MAX_VALUE, 1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.MATCH_NOT_FOUND.getCode()));
	}
}
