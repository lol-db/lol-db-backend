package com.riftdata.domain.match.controller;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.domain.player.dto.PositionLeader;
import com.riftdata.domain.player.service.PlayerSeasonReader;
import com.riftdata.domain.team.dto.TeamStanding;
import com.riftdata.domain.team.service.TeamStandingReader;
import com.riftdata.global.common.Position;
import com.riftdata.support.MatchFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
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
 * 홈 API (실제 MySQL, 끝나면 롤백).
 * 실데이터와 날짜가 겹치지 않게 테스트 경기를 2099-03-15에 넣는다.
 * 순위·시즌 등급·포지션 리더는 팀·선수 도메인 담당이라 테스트에서 직접 넣어 준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeControllerTests {

	private static final LocalDate DAY = LocalDate.of(2099, 3, 15);

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager em;
	@MockitoBean TeamStandingReader teamStandingReader;
	@MockitoBean PlayerSeasonReader playerSeasonReader;

	MatchFixture.Data data;

	@BeforeEach
	void setUp() {
		data = MatchFixture.create(em, DAY);
		Long a = data.teamA().getId();
		Long b = data.teamB().getId();
		Long aMid = data.playersA().get(Position.MID.ordinal()).getId();
		given(teamStandingReader.findTop(anyInt())).willReturn(List.of(
				new TeamStanding(a, 1, 6, 2, 6),
				new TeamStanding(b, 2, 5, 3, 3)));
		given(playerSeasonReader.findSeasonGrades(anyCollection())).willReturn(Map.of(aMid, "A"));
		given(playerSeasonReader.findPositionLeaders()).willReturn(List.of(
				new PositionLeader(Position.MID, aMid, "ZZA_MID", "ZZA", 78.6, "A-")));
	}

	@Test
	@DisplayName("홈 6개 영역을 한 번에 준다")
	void home() throws Exception {
		mockMvc.perform(get("/api/v1/home").param("date", DAY.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value("2099-03-15"))
				// 오늘 경기: 시작 시각 순 (경기 목록 API와 같은 모양)
				.andExpect(jsonPath("$.todayMatches.length()").value(2))
				.andExpect(jsonPath("$.todayMatches[0].matchId").value(data.finished().getId()))
				.andExpect(jsonPath("$.todayMatches[0].teamA.score").value(2))
				.andExpect(jsonPath("$.todayMatches[0].pog.name").value("ZZA_MID"))
				.andExpect(jsonPath("$.todayMatches[1].status").value("SCHEDULED"))
				// 최근 평점 상위: ZZA_MID 9.0·6.0·9.6 → 8.2, ZZB_MID 6.0·8.4·6.0 → 6.8, 나머지는 6.0
				.andExpect(jsonPath("$.topRatedPlayers.fromDate").value("2099-03-09"))
				.andExpect(jsonPath("$.topRatedPlayers.toDate").value("2099-03-15"))
				.andExpect(jsonPath("$.topRatedPlayers.minSets").value(3))
				.andExpect(jsonPath("$.topRatedPlayers.items.length()").value(5))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].rank").value(1))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].name").value("ZZA_MID"))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].teamCode").value("ZZA"))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].position").value("MID"))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].sets").value(3))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].mainChampion.name").value("ZZChamp2"))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].avgRating").value(8.2))
				.andExpect(jsonPath("$.topRatedPlayers.items[0].seasonGrade").value("A"))
				.andExpect(jsonPath("$.topRatedPlayers.items[1].name").value("ZZB_MID"))
				.andExpect(jsonPath("$.topRatedPlayers.items[1].avgRating").value(6.8))
				.andExpect(jsonPath("$.topRatedPlayers.items[1].seasonGrade").isEmpty())
				.andExpect(jsonPath("$.topRatedPlayers.items[2].avgRating").value(6.0))
				// 순위 미니표: 팀 코드는 team 테이블에서 채운다
				.andExpect(jsonPath("$.standingsTop.length()").value(2))
				.andExpect(jsonPath("$.standingsTop[0].rank").value(1))
				.andExpect(jsonPath("$.standingsTop[0].code").value("ZZA"))
				.andExpect(jsonPath("$.standingsTop[0].wins").value(6))
				.andExpect(jsonPath("$.standingsTop[0].setDiff").value(6))
				.andExpect(jsonPath("$.standingsTop[1].code").value("ZZB"))
				// 포지션 리더: 선수 도메인 결과 그대로
				.andExpect(jsonPath("$.positionLeaders.length()").value(1))
				.andExpect(jsonPath("$.positionLeaders[0].position").value("MID"))
				.andExpect(jsonPath("$.positionLeaders[0].grade").value("A-"))
				// 최근 POG: 평점 순 9.6(3세트) → 9.0(1세트) → 8.4(2세트)
				.andExpect(jsonPath("$.recentPogs.length()").value(3))
				.andExpect(jsonPath("$.recentPogs[0].setNo").value(3))
				.andExpect(jsonPath("$.recentPogs[0].rating").value(9.6))
				.andExpect(jsonPath("$.recentPogs[0].date").value("2099-03-15"))
				.andExpect(jsonPath("$.recentPogs[0].teamCode").value("ZZA"))
				.andExpect(jsonPath("$.recentPogs[0].champion.name").value("ZZChamp2"))
				.andExpect(jsonPath("$.recentPogs[1].setNo").value(1))
				.andExpect(jsonPath("$.recentPogs[2].name").value("ZZB_MID"))
				.andExpect(jsonPath("$.recentPogs[2].teamCode").value("ZZB"));
	}

	@Test
	@DisplayName("최근 POG는 3일, 평점 상위는 7일 안의 경기만 본다")
	void windows() throws Exception {
		// 경기 2일 뒤: 두 기간 모두 안
		mockMvc.perform(get("/api/v1/home").param("date", DAY.plusDays(2).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.todayMatches.length()").value(0))
				.andExpect(jsonPath("$.recentPogs.length()").value(3))
				.andExpect(jsonPath("$.topRatedPlayers.items.length()").value(5));
		// 3일 뒤: 최근 POG 기간 밖
		mockMvc.perform(get("/api/v1/home").param("date", DAY.plusDays(3).toString()))
				.andExpect(jsonPath("$.recentPogs.length()").value(0))
				.andExpect(jsonPath("$.topRatedPlayers.items.length()").value(5));
		// 6일 뒤: 평점 상위 기간의 첫날이라 포함
		mockMvc.perform(get("/api/v1/home").param("date", DAY.plusDays(6).toString()))
				.andExpect(jsonPath("$.topRatedPlayers.items.length()").value(5));
	}

	@Test
	@DisplayName("평점 상위 선수가 없으면 시즌 등급을 조회하지 않는다")
	void noTopRatedPlayers() throws Exception {
		mockMvc.perform(get("/api/v1/home").param("date", DAY.plusDays(7).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.topRatedPlayers.fromDate").value("2099-03-16"))
				.andExpect(jsonPath("$.topRatedPlayers.items.length()").value(0));

		then(playerSeasonReader).should(never()).findSeasonGrades(anyCollection());
	}
}
