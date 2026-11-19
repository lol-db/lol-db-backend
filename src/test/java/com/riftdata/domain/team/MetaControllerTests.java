package com.riftdata.domain.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import com.riftdata.domain.team.config.SeasonSettings;
import com.riftdata.domain.team.dto.LeaderResponse;
import com.riftdata.domain.team.dto.MetaResponse;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.MetaQueryRepository;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.domain.team.service.LeaderService;
import com.riftdata.domain.team.service.MetaService;
import com.riftdata.global.common.KstTime;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import com.riftdata.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메타(API-01), 검색(API-02), 지표 리더(API-09). 실제 MySQL에 테스트 데이터를 넣고 끝나면 롤백한다.
 * DB에 실제 데이터가 있어도 깨지지 않도록 테스트용 이름(Zq..., 2099년 stage)만 검사하거나,
 * 지표 리더는 실제 선수가 넘을 수 없는 값을 준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetaControllerTests {

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
	@Autowired MetaQueryRepository metaQueryRepository;
	@Autowired SeasonSettings seasonSettings;
	@Autowired LeaderService leaderService;

	private Team zqa;
	private Team zqb;
	private Stage spring;
	private Champion champion;
	private int gameSeq;

	@BeforeEach
	void setUp() {
		zqa = teamRepository.save(Team.builder().code("ZQA").build());
		zqb = teamRepository.save(Team.builder().code("ZQB").build());
		spring = stageRepository.save(Stage.builder().name("Zq Spring").stageOrder(97)
				.startDate(LocalDate.of(2099, 4, 1)).endDate(LocalDate.of(2099, 5, 31)).regular(true).build());
		champion = championRepository.save(Champion.builder().name("Zqchamp").build());
	}

	@Test
	@DisplayName("메타는 시즌 설정값, 패치(숫자순), stage, 현재 stage를 준다")
	void meta() {
		LckMatch match = matchRepository.save(LckMatch.builder().stage(spring).sets(3).team1(zqa).team2(zqb)
				.matchTime(LocalDateTime.of(2099, 4, 2, 17, 0)).build());
		setRepository.save(MatchSet.builder().match(match).setNumber(1).gameId("zq-meta-1")
				.version("99.10").playTime(LocalTime.of(0, 30)).build());
		setRepository.save(MatchSet.builder().match(match).setNumber(2).gameId("zq-meta-2")
				.version("99.9").playTime(LocalTime.of(0, 30)).build());

		MetaResponse meta = metaServiceOn(LocalDate.of(2099, 4, 15)).getMeta();

		assertThat(meta.season().name()).isEqualTo("2026 LCK");
		assertThat(meta.season().playoffTeamCount()).isEqualTo(6);
		assertThat(meta.season().teamCount()).isGreaterThanOrEqualTo(2);
		assertThat(meta.season().currentStage().stageId()).isEqualTo(spring.getId());
		assertThat(meta.season().endDate()).isEqualTo(LocalDate.of(2099, 5, 31));
		assertThat(meta.minSetsForGrade()).isEqualTo(10);
		assertThat(meta.patches()).containsSubsequence("99.9", "99.10");
		assertThat(meta.stages()).extracting(MetaResponse.Stage::name).contains("Zq Spring");
	}

	@Test
	@DisplayName("오늘이 어느 stage에도 들지 않으면 currentStage는 null")
	void metaWithoutCurrentStage() {
		assertThat(metaServiceOn(LocalDate.of(2100, 1, 1)).getMeta().season().currentStage()).isNull();
	}

	@Test
	@DisplayName("마지막 갱신 시각은 선수 시즌 지표 계산 시각의 최댓값 (+09:00)")
	void metaLastUpdatedAt() throws Exception {
		Player p = playerRepository.save(Player.builder().team(zqa).nickname("ZqTime").position(Position.MID).build());
		seasonRepository.save(PlayerSeason.builder().player(p).sets(1)
				.calculatedAt(LocalDateTime.of(2099, 12, 31, 23, 10)).build());
		mockMvc.perform(get("/api/v1/meta"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lastUpdatedAt").value("2099-12-31T23:10:00+09:00"))
				.andExpect(jsonPath("$.dataSource").value("Oracle's Elixir (2026 LCK)"));
	}

	@Test
	@DisplayName("검색은 대소문자를 무시하고 앞부분 일치를 먼저 준다")
	void search() throws Exception {
		playerRepository.save(Player.builder().team(zqa).nickname("xZqmid").position(Position.MID).build());
		playerRepository.save(Player.builder().team(zqa).nickname("ZqTop").position(Position.TOP).build());

		mockMvc.perform(get("/api/v1/search").param("q", "zq"))
				.andExpect(status().isOk())
				// 앞부분 일치(ZqTop)가 중간 일치(xZqmid)보다 먼저
				.andExpect(jsonPath("$.players[*].name").value(contains("ZqTop", "xZqmid")))
				.andExpect(jsonPath("$.players[0].teamCode").value("ZQA"))
				.andExpect(jsonPath("$.players[0].position").value("TOP"))
				.andExpect(jsonPath("$.teams[*].code").value(contains("ZQA", "ZQB")))
				.andExpect(jsonPath("$.champions[*].name").value(contains("Zqchamp")));
	}

	@Test
	@DisplayName("검색 limit은 종류별 최대 개수다")
	void searchLimit() throws Exception {
		mockMvc.perform(get("/api/v1/search").param("q", "zq").param("limit", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teams.length()").value(1))
				.andExpect(jsonPath("$.teams[0].code").value("ZQA"));
	}

	@Test
	@DisplayName("검색어의 %, _는 글자 그대로 찾는다")
	void searchEscapesWildcards() throws Exception {
		mockMvc.perform(get("/api/v1/search").param("q", "z_a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teams[*].code").value(not(hasItem("ZQA"))));
	}

	@Test
	@DisplayName("검색어가 비었거나 limit이 1~20 밖이면 400")
	void searchRejectsBadInput() throws Exception {
		for (String[] params : List.of(new String[] {"q", " "}, new String[] {"limit", "5"},
				new String[] {"q", "zq", "limit", "0"}, new String[] {"q", "zq", "limit", "21"})) {
			var request = get("/api/v1/search");
			for (int i = 0; i < params.length; i += 2) {
				request.param(params[i], params[i + 1]);
			}
			mockMvc.perform(request)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()));
		}
	}

	@Test
	@DisplayName("지표 리더는 10세트 이상 선수 가운데 지표마다 1위를 준다")
	void leaders() {
		// ZqLead(ZQA MID): 10세트, 매 세트 20/0/10, 피해 60,000, 시야 300, 15분 골드 9,000, 100점, 30분
		// ZqLane(ZQB MID): 같은 10세트의 상대 라이너, 15분 골드 3,000
		// ZqFew (ZQA TOP): 9세트뿐이라 값이 더 커도 제외
		Player lead = playerRepository.save(Player.builder().team(zqa).nickname("ZqLead").position(Position.MID).build());
		Player lane = playerRepository.save(Player.builder().team(zqb).nickname("ZqLane").position(Position.MID).build());
		Player few = playerRepository.save(Player.builder().team(zqa).nickname("ZqFew").position(Position.TOP).build());
		// 세트 번호는 1~5라서 Bo5 두 경기로 10세트를 만든다
		List<LckMatch> matches = List.of(
				matchRepository.save(LckMatch.builder().stage(spring).sets(5).team1(zqa).team2(zqb)
						.matchTime(LocalDateTime.of(2099, 4, 3, 17, 0)).build()),
				matchRepository.save(LckMatch.builder().stage(spring).sets(5).team1(zqa).team2(zqb)
						.matchTime(LocalDateTime.of(2099, 4, 4, 17, 0)).build()));
		for (int i = 1; i <= 10; i++) {
			MatchSet set = setRepository.save(MatchSet.builder().match(matches.get((i - 1) / 5)).setNumber((i - 1) % 5 + 1)
					.gameId("zq-lead-" + (++gameSeq)).version("99.1").playTime(LocalTime.of(0, 30)).build());
			TeamSet a = teamSetRepository.save(TeamSet.builder().matchSet(set).team(zqa).side(Side.BLUE).victory(true).build());
			TeamSet b = teamSetRepository.save(TeamSet.builder().matchSet(set).team(zqb).side(Side.RED).victory(false).build());
			playerSetRepository.save(PlayerSet.builder().teamSet(a).position(Position.MID).player(lead).champion(champion)
					.kills(20).deaths(0).assists(10).damageToChampions(60000).visionPoints(300)
					.goldAt15(9000).totalPoints(100).build());
			playerSetRepository.save(PlayerSet.builder().teamSet(b).position(Position.MID).player(lane).champion(champion)
					.kills(0).deaths(5).assists(0).goldAt15(3000).totalPoints(0).build());
			if (i <= 9) {
				playerSetRepository.save(PlayerSet.builder().teamSet(a).position(Position.TOP).player(few).champion(champion)
						.kills(100).deaths(0).assists(100).damageToChampions(900000).visionPoints(600)
						.goldAt15(20000).totalPoints(100).build());
			}
		}

		Map<String, LeaderResponse> byMetric = leaderService.getLeaders().stream()
				.collect(Collectors.toMap(LeaderResponse::metric, Function.identity()));

		assertThat(byMetric).containsOnlyKeys("KDA", "DPM", "GD15", "VSPM", "AVG_RATING");
		assertThat(byMetric.values()).extracting(LeaderResponse::name).containsOnly("ZqLead");
		assertThat(byMetric.get("KDA").value()).isEqualTo(300.0);       // (200 + 100) / max(0, 1)
		assertThat(byMetric.get("DPM").value()).isEqualTo(2000);        // 600,000 / 300분
		assertThat(byMetric.get("GD15").value()).isEqualTo(6000);       // 9,000 − 3,000
		assertThat(byMetric.get("VSPM").value()).isEqualTo(10.0);       // 3,000 / 300분
		assertThat(byMetric.get("AVG_RATING").value()).isEqualTo(10.0); // 100점 → 10.0
		assertThat(byMetric.get("KDA").teamCode()).isEqualTo("ZQA");
		assertThat(byMetric.get("KDA").playerId()).isEqualTo(lead.getId());
	}

	private MetaService metaServiceOn(LocalDate today) {
		Clock clock = Clock.fixed(today.atTime(LocalTime.NOON).atZone(KstTime.ZONE).toInstant(), KstTime.ZONE);
		return new MetaService(metaQueryRepository, seasonSettings, clock);
	}
}
