package com.riftdata.domain.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
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
import com.riftdata.domain.team.dto.TeamStanding;
import com.riftdata.domain.team.dto.StandingResponse;
import com.riftdata.domain.team.dto.TeamDetailResponse;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.domain.team.service.TeamService;
import com.riftdata.domain.team.service.TeamStandingReader;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import com.riftdata.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
	@Autowired TeamStandingReader teamStandingReader;
	@Autowired TeamService teamService;

	private Team tsa;
	private Team tsb;
	private Team tsc;
	private Stage regular;
	private LckMatch m1;
	private int gameSeq;

	@BeforeEach
	void setUp() {
		tsa = teamRepository.save(Team.builder().code("TSA").build());
		tsb = teamRepository.save(Team.builder().code("TSB").build());
		tsc = teamRepository.save(Team.builder().code("TSC").build());
		regular = stageRepository.save(stage("Test Regular", 98, true));
		Stage playoff = stageRepository.save(stage("Test Playoff", 99, false));

		m1 = match(regular, 3, tsa, tsb, 1);
		TeamSet[] m1Set1 = set(m1, 1, tsa, tsb);
		set(m1, 2, tsb, tsa);
		set(m1, 3, tsa, tsb);

		LckMatch m2 = match(regular, 3, tsa, tsc, 2);
		TeamSet[] m2Set1 = set(m2, 1, tsa, tsc);
		TeamSet[] m2Set2 = set(m2, 2, tsa, tsc);

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
		// TSA 선수 i의 M1 1세트: 킬 i, 데스 1(SUP만 0), 어시스트 2, 총 점수 50 + 10i
		// TSA MID(i=2)는 M2 1·2세트를 OtherChamp로 더 뛴다 → 대표 챔피언 OtherChamp
		Champion champion = championRepository.save(Champion.builder().name("TestChamp").build());
		Champion other = championRepository.save(Champion.builder().name("OtherChamp").build());
		Position[] positions = Position.values();
		for (int i = 0; i < positions.length; i++) {
			Player a = playerRepository.save(Player.builder()
					.team(tsa).nickname("TsaP" + i).position(positions[i]).build());
			Player b = playerRepository.save(Player.builder()
					.team(tsb).nickname("TsbP" + i).position(positions[i]).build());
			playerSetRepository.save(PlayerSet.builder().teamSet(m1Set1[0]).position(positions[i])
					.player(a).champion(champion).goldAt15(3000)
					.kills(i).deaths(i == 4 ? 0 : 1).assists(2).totalPoints(50 + 10 * i).build());
			playerSetRepository.save(PlayerSet.builder().teamSet(m1Set1[1]).position(positions[i])
					.player(b).champion(champion).goldAt15(2800).build());
			if (i == 2) {
				seasonRepository.save(PlayerSeason.builder().player(a).sets(12).totalPoints(81)
						.calculatedAt(LocalDateTime.of(2026, 1, 31, 0, 0)).build());
				playerSetRepository.save(PlayerSet.builder().teamSet(m2Set1[0]).position(positions[i])
						.player(a).champion(other).kills(3).deaths(0).assists(4).totalPoints(90).build());
				playerSetRepository.save(PlayerSet.builder().teamSet(m2Set2[0]).position(positions[i])
						.player(a).champion(other).kills(1).deaths(2).assists(0).build());
			}
		}
	}

	@Test
	@DisplayName("팀 목록은 teamId, code만 준다 (팀명 컬럼이 없어 name 필드 없음)")
	void listsTeams() throws Exception {
		mockMvc.perform(get("/api/v1/teams"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == 'TSA')].teamId").value(tsa.getId().intValue()))
				.andExpect(jsonPath("$[0].name").doesNotExist());
	}

	@Test
	@DisplayName("순위표는 끝난 정규 시즌 경기만 집계하고 명세 형식(asOf, playoffTeamCount, rows)으로 준다")
	void standingsCountOnlyFinishedRegularMatches() throws Exception {
		mockMvc.perform(get("/api/v1/standings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playoffTeamCount").value(6))
				.andExpect(jsonPath("$.asOf").exists())
				.andExpect(jsonPath("$.rows[0].name").doesNotExist())
				.andExpect(jsonPath("$.rows[?(@.code == 'TSA')].matches").value(2))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSA')].wins").value(2))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSA')].setWins").value(4))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSA')].setLosses").value(1))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSA')].winRate").value(1.0))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSB')].wins").value(1))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSB')].winRate").value(0.5))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSB')].setDiff").value(1))
				// TSB: M1(1일) 패 → M3(3일) 승. recent5는 최근 경기부터
				.andExpect(jsonPath("$.rows[?(@.code == 'TSB')].recent5[*]").value(contains("W", "L")))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSC')].losses").value(2))
				.andExpect(jsonPath("$.rows[?(@.code == 'TSC')].setDiff").value(-4));
	}

	@Test
	@DisplayName("두 팀이 승수·세트 득실까지 같으면 승자승으로 정한다")
	void headToHeadBreaksTwoWayTie() {
		// THA·THB 모두 1승 1패, 세트 3-3. 팀코드로는 THA가 앞이지만 맞대결은 THB가 이겼다.
		Team tha = teamRepository.save(Team.builder().code("THA").build());
		Team thb = teamRepository.save(Team.builder().code("THB").build());
		Team thy = teamRepository.save(Team.builder().code("THY").build());
		Team thz = teamRepository.save(Team.builder().code("THZ").build());
		playBo3(thb, tha, 11);
		playBo3(tha, thy, 12);
		playBo3(thz, thb, 13);

		assertThat(codesInOrder("THA", "THB")).containsExactly("THB", "THA");
		assertThat(rankOf("THB")).isEqualTo(rankOf("THA") - 1);
	}

	@Test
	@DisplayName("승자승까지 같은 순환 동률은 같은 순위이고 팀코드순으로 놓는다")
	void circularTieSharesRank() {
		// TCA > TCB > TCC > TCA, 세 팀 모두 1승 1패, 세트 3-3, 맞대결 1승씩
		Team tca = teamRepository.save(Team.builder().code("TCA").build());
		Team tcb = teamRepository.save(Team.builder().code("TCB").build());
		Team tcc = teamRepository.save(Team.builder().code("TCC").build());
		playBo3(tcc, tca, 14);
		playBo3(tcb, tcc, 15);
		playBo3(tca, tcb, 16);

		assertThat(codesInOrder("TCA", "TCB", "TCC")).containsExactly("TCA", "TCB", "TCC");
		assertThat(rankOf("TCB")).isEqualTo(rankOf("TCA"));
		assertThat(rankOf("TCC")).isEqualTo(rankOf("TCA"));
	}

	@Test
	@DisplayName("세 팀 동률이면 그 팀들끼리의 맞대결 승수만 센다")
	void headToHeadAmongThreeTeams() {
		// TTA·TTB·TTC 모두 2승 2패, 세트 6-6. 셋끼리는 TTC 2승, TTB 1승, TTA 0승
		Team tta = teamRepository.save(Team.builder().code("TTA").build());
		Team ttb = teamRepository.save(Team.builder().code("TTB").build());
		Team ttc = teamRepository.save(Team.builder().code("TTC").build());
		Team tto = teamRepository.save(Team.builder().code("TTO").build());
		playBo3(ttc, tta, 17);
		playBo3(ttc, ttb, 18);
		playBo3(ttb, tta, 19);
		// 묶음 밖 경기로 승패를 2승 2패로 맞춘다
		playBo3(tta, tto, 20);
		playBo3(tta, tto, 21);
		playBo3(ttb, tto, 22);
		playBo3(tto, ttb, 23);
		playBo3(tto, ttc, 24);
		playBo3(tto, ttc, 25);

		assertThat(codesInOrder("TTA", "TTB", "TTC")).containsExactly("TTC", "TTB", "TTA");
		assertThat(List.of(rankOf("TTB") - rankOf("TTC"), rankOf("TTA") - rankOf("TTB"))).containsExactly(1, 1);
	}

	@Test
	@DisplayName("다른 도메인용 순위(TeamStandingReader)는 순위표와 같은 값이다")
	void standingReaderMatchesStandings() {
		Map<Long, TeamStanding> byId = teamStandingReader.findByTeamIds(List.of(tsa.getId(), tsb.getId()));
		assertThat(byId).containsOnlyKeys(tsa.getId(), tsb.getId());
		TeamStanding a = byId.get(tsa.getId());
		TeamStanding b = byId.get(tsb.getId());
		assertThat(List.of(a.wins(), a.losses())).containsExactly(2, 0);
		assertThat(List.of(b.wins(), b.losses())).containsExactly(1, 1);
		assertThat(a.rank()).isEqualTo(rankOf("TSA"));
		assertThat(a.rank()).isLessThan(b.rank());
	}

	@Test
	@DisplayName("경기 상세가 팀 순위를 읽어 200으로 응답한다")
	void matchDetailUsesStandingReader() throws Exception {
		mockMvc.perform(get("/api/v1/matches/{id}", m1.getId()))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("팀 상세는 명세 형식으로 성적, 로스터, 팀 지표, 최근 경기를 준다")
	void teamDetail() throws Exception {
		mockMvc.perform(get("/api/v1/teams/{id}", tsa.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("TSA"))
				.andExpect(jsonPath("$.name").doesNotExist())
				.andExpect(jsonPath("$.coach").doesNotExist())
				.andExpect(jsonPath("$.rank").value(rankOf("TSA")))
				.andExpect(jsonPath("$.wins").value(2))
				.andExpect(jsonPath("$.losses").value(0))
				.andExpect(jsonPath("$.setWins").value(4))
				.andExpect(jsonPath("$.setLosses").value(1))
				// 로스터: 포지션 순, name은 닉네임, 등급은 아직 null
				.andExpect(jsonPath("$.roster.length()").value(5))
				.andExpect(jsonPath("$.roster[0].position").value("TOP"))
				.andExpect(jsonPath("$.roster[2].name").value("TsaP2"))
				.andExpect(jsonPath("$.roster[2].grade").value(nullValue()))
				// 세트 9개 (M1 3, M2 2, M4 1, M5 3). 이긴 세트는 모두 블루, 진 세트는 모두 레드
				.andExpect(jsonPath("$.teamStats.length()").value(7))
				.andExpect(jsonPath("$.teamStats[0].key").value("GD15"))
				.andExpect(jsonPath("$.teamStats[0].value").value(1000.0))
				.andExpect(jsonPath("$.teamStats[?(@.key == 'FIRST_DRAGON_RATE')].value").value(contains(0.444)))
				.andExpect(jsonPath("$.teamStats[?(@.key == 'AVG_DURATION_SEC')].value").value(contains(1800)))
				.andExpect(jsonPath("$.teamStats[?(@.key == 'BLUE_WIN_RATE')].value").value(contains(1.0)))
				.andExpect(jsonPath("$.teamStats[?(@.key == 'BLUE_WIN_RATE')].leagueRank").value(contains((Object) null)))
				.andExpect(jsonPath("$.teamStats[?(@.key == 'RED_WIN_RATE')].value").value(contains(0.0)))
				// 최근 경기: 끝난 경기만 최신순 (M5 → M2 → M1), M4는 진행 중이라 빠진다
				.andExpect(jsonPath("$.recentMatches.length()").value(3))
				.andExpect(jsonPath("$.recentMatches[0].date").value("2026-01-05"))
				.andExpect(jsonPath("$.recentMatches[0].opponent.code").value("TSC"))
				.andExpect(jsonPath("$.recentMatches[0].opponent.teamId").value(tsc.getId().intValue()))
				.andExpect(jsonPath("$.recentMatches[0].score").value("0:3"))
				.andExpect(jsonPath("$.recentMatches[0].win").value(false))
				.andExpect(jsonPath("$.recentMatches[2].opponent.code").value("TSB"))
				.andExpect(jsonPath("$.recentMatches[2].score").value("2:1"))
				.andExpect(jsonPath("$.recentMatches[2].win").value(true));
	}

	@Test
	@DisplayName("로스터에 평균 평점, KDA, 대표 챔피언이 들어 있다")
	void rosterRecords() throws Exception {
		mockMvc.perform(get("/api/v1/teams/{id}", tsa.getId()))
				.andExpect(status().isOk())
				// TOP: 1세트, 0/1/2, 50점 → 평점 6.0, KDA 2.0
				.andExpect(jsonPath("$.roster[0].sets").value(1))
				.andExpect(jsonPath("$.roster[0].avgRating").value(6.0))
				.andExpect(jsonPath("$.roster[0].kda").value(2.0))
				.andExpect(jsonPath("$.roster[0].mainChampion.name").value("TestChamp"))
				// MID: 3세트, 6/3/6 → KDA 4.0. 평점은 70점(7.6)·90점(9.2)만 평균 → 8.4 (점수 없는 세트 제외)
				.andExpect(jsonPath("$.roster[2].sets").value(3))
				.andExpect(jsonPath("$.roster[2].kda").value(4.0))
				.andExpect(jsonPath("$.roster[2].avgRating").value(8.4))
				.andExpect(jsonPath("$.roster[2].mainChampion.name").value("OtherChamp"))
				// SUP: 데스 0이면 KDA = 킬 + 어시스트 = 6
				.andExpect(jsonPath("$.roster[4].kda").value(6.0));
	}

	@Test
	@DisplayName("세트 기록이 없는 선수는 KDA·평점·대표 챔피언이 null이다")
	void rosterPlayerWithoutSets() throws Exception {
		playerRepository.save(Player.builder()
				.team(tsa).nickname("TsaBench").position(Position.TOP).build());
		mockMvc.perform(get("/api/v1/teams/{id}", tsa.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roster[?(@.name == 'TsaBench')].sets").value(contains(0)))
				.andExpect(jsonPath("$.roster[?(@.name == 'TsaBench')].kda").value(contains((Object) null)))
				.andExpect(jsonPath("$.roster[?(@.name == 'TsaBench')].avgRating").value(contains((Object) null)))
				.andExpect(jsonPath("$.roster[?(@.name == 'TsaBench')].mainChampion").value(contains((Object) null)));
	}

	@Test
	@DisplayName("골드 차와 첫 오브젝트 획득률에 리그 내 순위가 붙는다")
	void statRanks() {
		TeamDetailResponse a = teamService.getTeam(tsa.getId());
		TeamDetailResponse b = teamService.getTeam(tsb.getId());
		// 15분 골드 차: TSA +1,000, TSB −1,000
		assertThat(leagueRank(a, "GD15")).isLessThan(leagueRank(b, "GD15"));
		assertThat(leagueRank(a, "FIRST_DRAGON_RATE")).isNotNull();
		assertThat(leagueRank(a, "AVG_DURATION_SEC")).isNull();
		assertThat(leagueRank(a, "RED_WIN_RATE")).isNull();
	}

	@Test
	@DisplayName("경기가 없는 팀은 0승 0패, 지표 값과 순위는 null이다")
	void teamWithoutMatches() throws Exception {
		Team empty = teamRepository.save(Team.builder().code("TSD").build());
		mockMvc.perform(get("/api/v1/teams/{id}", empty.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wins").value(0))
				.andExpect(jsonPath("$.losses").value(0))
				.andExpect(jsonPath("$.teamStats.length()").value(7))
				.andExpect(jsonPath("$.teamStats[*].value").value(everyItem(nullValue())))
				.andExpect(jsonPath("$.teamStats[*].leagueRank").value(everyItem(nullValue())))
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

	/** 정규 시즌 Bo3 한 경기를 2-1로 치른다. */
	private void playBo3(Team winner, Team loser, int day) {
		LckMatch match = match(regular, 3, winner, loser, day);
		set(match, 1, winner, loser);
		set(match, 2, loser, winner);
		set(match, 3, winner, loser);
	}

	private int rankOf(String code) {
		return teamService.getStandings().stream()
				.filter(row -> row.code().equals(code))
				.findFirst()
				.orElseThrow()
				.rank();
	}

	private static Integer leagueRank(TeamDetailResponse team, String key) {
		return team.teamStats().stream().filter(stat -> stat.key().equals(key)).findFirst().orElseThrow().leagueRank();
	}

	/** 순위표에서 주어진 팀코드만 순위 순으로 뽑는다. */
	private List<String> codesInOrder(String... codes) {
		List<String> wanted = List.of(codes);
		return teamService.getStandings().stream()
				.map(StandingResponse::code)
				.filter(wanted::contains)
				.toList();
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
