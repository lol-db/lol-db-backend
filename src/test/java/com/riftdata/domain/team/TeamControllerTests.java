package com.riftdata.domain.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.domain.champion.entity.Champion;
import com.riftdata.domain.champion.repository.ChampionRepository;
import com.riftdata.domain.match.entity.LckMatch;
import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.match.entity.Stage;
import com.riftdata.domain.match.repository.LckMatchRepository;
import com.riftdata.domain.match.repository.MatchSetRepository;
import com.riftdata.domain.match.repository.StageRepository;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.domain.record.entity.PlayerSet;
import com.riftdata.domain.record.entity.TeamSet;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.record.repository.TeamSetRepository;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import com.riftdata.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 MySQL에 가상 경기를 넣고 팀 API 응답을 확인한다. 테스트가 끝나면 롤백된다.
 * DB에 실제 데이터가 있어도 깨지지 않도록 테스트용 팀코드(TSA, TSB, TSC) 행만 검사한다.
 *
 * <pre>
 * 정규 M1: TSA 2-1 TSB     정규 M2: TSA 2-0 TSC     정규 M3: TSB 2-0 TSC
 * 정규 M4: TSA 0-1 TSC (진행 중, 집계 제외)
 * 플레이오프 M5: TSC 3-0 TSA (순위표 제외, 최근 경기 포함)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerTests {

	@Autowired MockMvc mockMvc;
	@Autowired TeamRepository teamRepository;
	@Autowired PlayerRepository playerRepository;
	@Autowired PlayerSeasonRepository seasonRepository;
	@Autowired ChampionRepository championRepository;
	@Autowired StageRepository stageRepository;
	@Autowired LckMatchRepository matchRepository;
	@Autowired MatchSetRepository setRepository;
	@Autowired TeamSetRepository teamSetRepository;
	@Autowired PlayerSetRepository playerSetRepository;

	private Team tsa;
	private Team tsb;
	private Team tsc;
	private int gameSeq;

	@BeforeEach
	void setUp() {
		tsa = teamRepository.save(Team.builder().code("TSA").build());
		tsb = teamRepository.save(Team.builder().code("TSB").build());
		tsc = teamRepository.save(Team.builder().code("TSC").build());
		Stage regular = stageRepository.save(stage("Test Regular", 98, true));
		Stage playoff = stageRepository.save(stage("Test Playoff", 99, false));

		LckMatch m1 = match(regular, 3, tsa, tsb, 1);
		TeamSet[] m1Set1 = set(m1, 1, tsa, tsb);
		set(m1, 2, tsb, tsa);
		set(m1, 3, tsa, tsb);

		LckMatch m2 = match(regular, 3, tsa, tsc, 2);
		set(m2, 1, tsa, tsc);
		set(m2, 2, tsa, tsc);

		LckMatch m3 = match(regular, 3, tsb, tsc, 3);
		set(m3, 1, tsb, tsc);
		set(m3, 2, tsb, tsc);

		LckMatch m4 = match(regular, 3, tsa, tsc, 4);
		set(m4, 1, tsc, tsa);

		LckMatch m5 = match(playoff, 5, tsc, tsa, 5);
		set(m5, 1, tsc, tsa);
		set(m5, 2, tsc, tsa);
		set(m5, 3, tsc, tsa);

		// 15분 골드는 M1 1세트에만 넣는다: TSA 5 × 3,000 vs TSB 5 × 2,800 → +1,000
		Champion champion = championRepository.save(Champion.builder().name("TestChamp").build());
		Position[] positions = Position.values();
		for (int i = 0; i < positions.length; i++) {
			Player a = playerRepository.save(Player.builder()
					.team(tsa).nickname("TsaP" + i).position(positions[i]).build());
			Player b = playerRepository.save(Player.builder()
					.team(tsb).nickname("TsbP" + i).position(positions[i]).build());
			playerSetRepository.save(PlayerSet.builder().teamSet(m1Set1[0]).position(positions[i])
					.player(a).champion(champion).goldAt15(3000).build());
			playerSetRepository.save(PlayerSet.builder().teamSet(m1Set1[1]).position(positions[i])
					.player(b).champion(champion).goldAt15(2800).build());
			if (i == 2) {
				seasonRepository.save(PlayerSeason.builder().player(a).sets(12).totalPoints(81)
						.calculatedAt(LocalDateTime.of(2026, 1, 31, 0, 0)).build());
			}
		}
	}

	@Test
	@DisplayName("팀 목록에 새 팀이 들어 있다")
	void listsTeams() throws Exception {
		mockMvc.perform(get("/api/v1/teams"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == 'TSA')].teamId").value(tsa.getId().intValue()));
	}

	@Test
	@DisplayName("순위표는 끝난 정규 시즌 경기만 집계한다")
	void standingsCountOnlyFinishedRegularMatches() throws Exception {
		mockMvc.perform(get("/api/v1/standings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == 'TSA')].matches").value(2))
				.andExpect(jsonPath("$[?(@.code == 'TSA')].wins").value(2))
				.andExpect(jsonPath("$[?(@.code == 'TSA')].setWins").value(4))
				.andExpect(jsonPath("$[?(@.code == 'TSA')].setLosses").value(1))
				.andExpect(jsonPath("$[?(@.code == 'TSA')].winRate").value(100))
				.andExpect(jsonPath("$[?(@.code == 'TSA')].recent[*]").value(org.hamcrest.Matchers.contains("W", "W")))
				.andExpect(jsonPath("$[?(@.code == 'TSB')].wins").value(1))
				.andExpect(jsonPath("$[?(@.code == 'TSB')].setDiff").value(1))
				.andExpect(jsonPath("$[?(@.code == 'TSC')].losses").value(2))
				.andExpect(jsonPath("$[?(@.code == 'TSC')].setDiff").value(-4));
	}

	@Test
	@DisplayName("팀 상세는 성적, 로스터, 지표, 최근 경기를 돌려준다")
	void teamDetail() throws Exception {
		mockMvc.perform(get("/api/v1/teams/{id}", tsa.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("TSA"))
				.andExpect(jsonPath("$.standing.wins").value(2))
				// 로스터: 포지션 순, MID 선수만 시즌 지표가 있다
				.andExpect(jsonPath("$.roster.length()").value(5))
				.andExpect(jsonPath("$.roster[0].position").value("TOP"))
				.andExpect(jsonPath("$.roster[2].totalPoints").value(81))
				.andExpect(jsonPath("$.roster[0].totalPoints").doesNotExist())
				// 세트 9개 (M1 3, M2 2, M4 1, M5 3). 이긴 세트는 모두 블루, 진 세트는 모두 레드
				.andExpect(jsonPath("$.stats.sets").value(9))
				.andExpect(jsonPath("$.stats.blueWinRate").value(100))
				.andExpect(jsonPath("$.stats.redWinRate").value(0))
				.andExpect(jsonPath("$.stats.firstDragonRate").value(44))
				.andExpect(jsonPath("$.stats.goldDiffAt15").value(1000))
				.andExpect(jsonPath("$.stats.avgPlaySeconds").value(1800))
				// 최근 경기: 끝난 경기만 최신순 (M5 → M2 → M1), M4는 진행 중이라 빠진다
				.andExpect(jsonPath("$.recentMatches.length()").value(3))
				.andExpect(jsonPath("$.recentMatches[0].stageName").value("Test Playoff"))
				.andExpect(jsonPath("$.recentMatches[0].opponentCode").value("TSC"))
				.andExpect(jsonPath("$.recentMatches[0].setWins").value(0))
				.andExpect(jsonPath("$.recentMatches[0].setLosses").value(3))
				.andExpect(jsonPath("$.recentMatches[0].win").value(false))
				.andExpect(jsonPath("$.recentMatches[2].opponentCode").value("TSB"))
				.andExpect(jsonPath("$.recentMatches[2].win").value(true));
	}

	@Test
	@DisplayName("경기가 없는 팀은 0승 0패, 지표는 null이다")
	void teamWithoutMatches() throws Exception {
		Team empty = teamRepository.save(Team.builder().code("TSD").build());
		mockMvc.perform(get("/api/v1/teams/{id}", empty.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.standing.matches").value(0))
				.andExpect(jsonPath("$.standing.winRate").value(0))
				.andExpect(jsonPath("$.stats.sets").value(0))
				.andExpect(jsonPath("$.stats.blueWinRate").doesNotExist())
				.andExpect(jsonPath("$.roster.length()").value(0))
				.andExpect(jsonPath("$.recentMatches.length()").value(0));
	}

	@Test
	@DisplayName("없는 팀은 404 TEAM_001")
	void unknownTeam() throws Exception {
		mockMvc.perform(get("/api/v1/teams/{id}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.TEAM_NOT_FOUND.getCode()));
	}

	private Stage stage(String name, int order, boolean regular) {
		return Stage.builder().name(name).stageOrder(order)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 3, 1)).regular(regular).build();
	}

	private LckMatch match(Stage stage, int bestOf, Team team1, Team team2, int day) {
		return matchRepository.save(LckMatch.builder().stage(stage).sets(bestOf).team1(team1).team2(team2)
				.matchTime(LocalDateTime.of(2026, 1, day, 17, 0)).build());
	}

	/** 이긴 팀은 블루 + 첫 용, 진 팀은 레드. 세트 길이는 모두 30분. */
	private TeamSet[] set(LckMatch match, int number, Team winner, Team loser) {
		MatchSet set = setRepository.save(MatchSet.builder().match(match).setNumber(number)
				.gameId("test-team-api-" + (++gameSeq)).version("26.01").playTime(LocalTime.of(0, 30)).build());
		TeamSet win = teamSetRepository.save(TeamSet.builder().matchSet(set).team(winner)
				.side(Side.BLUE).victory(true).firstDragon(true).build());
		TeamSet lose = teamSetRepository.save(TeamSet.builder().matchSet(set).team(loser)
				.side(Side.RED).victory(false).build());
		return new TeamSet[] {win, lose};
	}
}
