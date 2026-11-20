package com.riftdata.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.batch.OeGameReader.LoadValidationException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV 적재 통합 테스트. 실제 MySQL에 적재하고 테스트가 끝나면 롤백한다.
 *
 * <p>Team A ↔ Team B (세트마다 UTC 08:10, 08:20 … = 한국 17:10, 17:20 …)
 * <ul>
 *   <li>4/1 정규: A 2:1 (3세트) / 4/2 정규: B 2:0 / 4/3 정규: A 2:1 / 4/10 플레이오프: A 3:1 (Bo5)</li>
 *   <li>모두 12세트라 선수 10명 모두 시즌 10세트 이상</li>
 *   <li>A의 MID는 모든 세트에서 킬·피해량을 크게 올려 B의 MID보다 점수가 높아야 한다.</li>
 *   <li>제외 대상: 다른 리그(LCK CL) 1경기, LCK Cup 1경기, 미완성 1경기, 2025년 1경기</li>
 * </ul>
 */
@SpringBootTest(properties = {
		"loader.team-codes.[Team\\ A]=TMA",
		"loader.team-codes.[Team\\ B]=TMB"})
@AutoConfigureMockMvc
@Transactional
class LckCsvLoaderTests {

	private static final String REGULAR = "Rounds 1-2";

	@Autowired LckCsvLoader loader;
	@Autowired JdbcTemplate jdbc;
	@Autowired MockMvc mockMvc;
	@TempDir Path dir;

	@Test
	@DisplayName("경기·세트·기록을 적재하고 시리즈를 묶어 Bo3/Bo5를 정한다")
	void loadsSeason() throws Exception {
		LckCsvLoader.Result result = loader.load(season().write(dir), false);

		assertThat(result.csvGames()).isEqualTo(12);
		assertThat(result.loadedGames()).isEqualTo(12);
		assertThat(result.newMatches()).isEqualTo(4);
		assertThat(result.otherLeagueRows()).isEqualTo(12);
		assertThat(result.excludedSplitRows()).isEqualTo(12);
		assertThat(result.incompleteRows()).isEqualTo(12);
		assertThat(result.otherYearRows()).isEqualTo(12);

		assertThat(count("team WHERE code IN ('TMA', 'TMB')")).isEqualTo(2);
		assertThat(count("player WHERE nickname LIKE 'A\\_%' OR nickname LIKE 'B\\_%'")).isEqualTo(10);
		assertThat(jdbc.queryForList("SELECT name FROM stage ORDER BY stage_order", String.class))
				.containsExactly(REGULAR, REGULAR + " 플레이오프");
		assertThat(jdbc.queryForList("SELECT is_regular FROM stage ORDER BY stage_order", Boolean.class))
				.containsExactly(true, false);

		// 시리즈 첫 세트 UTC 08:10 → 한국 17:10, 시리즈마다 세트 수, Bo3 3개와 Bo5 1개
		assertThat(jdbc.queryForList("SELECT DATE_FORMAT(match_time, '%m-%d %H:%i') FROM lck_match ORDER BY match_time",
				String.class)).containsExactly("04-01 17:10", "04-02 17:10", "04-03 17:10", "04-10 17:10");
		assertThat(jdbc.queryForList("SELECT sets FROM lck_match ORDER BY match_time", Integer.class))
				.containsExactly(3, 3, 3, 5);
		assertThat(jdbc.queryForList("""
				SELECT COUNT(*) FROM match_set ms JOIN lck_match m ON m.match_id = ms.match_id
				GROUP BY m.match_id ORDER BY MIN(m.match_time)""", Integer.class)).containsExactly(3, 2, 3, 4);
		assertThat(count("match_set")).isEqualTo(12);
		assertThat(count("team_set")).isEqualTo(24);
		assertThat(count("player_set")).isEqualTo(120);
		assertThat(count("ban")).isEqualTo(120);

		// 원천 값 매핑: 팀 오브젝트, 소수 원천값 반올림, 15분 지표
		assertThat(jdbc.queryForObject("SELECT SUM(dragons) FROM team_set WHERE victory = 1", Integer.class)).isEqualTo(48);
		assertThat(jdbc.queryForObject("SELECT MAX(damage_taken_pm) FROM player_set", Integer.class)).isEqualTo(612);
		assertThat(jdbc.queryForObject("SELECT MIN(gold_at_15) FROM player_set", Integer.class)).isEqualTo(5700);
		assertThat(jdbc.queryForObject("SELECT MAX(gold_at_15) FROM player_set", Integer.class)).isEqualTo(7000);
		assertThat(jdbc.queryForObject("SELECT play_time FROM match_set LIMIT 1", String.class)).isEqualTo("00:30:00");
	}

	@Test
	@DisplayName("세트 점수는 0~100, POG는 세트마다 승리 팀에서 한 명, 시즌 지표는 같은 포지션 안에서 순위")
	void recalculatesScores() throws Exception {
		LckCsvLoader.Result result = loader.load(season().write(dir), false);

		assertThat(count("player_set WHERE total_points IS NULL")).isZero();
		assertThat(count("player_set WHERE total_points < 0 OR total_points > 100")).isZero();
		assertThat(result.recalculation().pogs()).isEqualTo(12);
		assertThat(jdbc.queryForList("""
				SELECT COUNT(*) FROM player_set ps JOIN team_set ts ON ts.team_set_id = ps.team_set_id
				WHERE ps.is_pog = 1 GROUP BY ts.set_id""", Integer.class)).hasSize(12).containsOnly(1);
		assertThat(count("player_set ps JOIN team_set ts ON ts.team_set_id = ps.team_set_id WHERE ps.is_pog = 1 AND ts.victory = 0"))
				.isZero();

		assertThat(count("player_season")).isEqualTo(10);
		assertThat(count("player_season WHERE total_points IS NULL")).isZero();
		assertThat(jdbc.queryForList("SELECT sets FROM player_season", Integer.class)).containsOnly(12);
		Map<String, Object> aMid = season("A_mid");
		Map<String, Object> bMid = season("B_mid");
		assertThat(((Number) aMid.get("total_points")).intValue())
				.isGreaterThan(((Number) bMid.get("total_points")).intValue());
		assertThat(((Number) aMid.get("rank_in_position")).intValue()).isEqualTo(1);
		assertThat(((Number) bMid.get("rank_in_position")).intValue()).isEqualTo(2);

		// API에서 바로 보인다: MID 랭킹 1위 A_mid, 등급 있음
		mockMvc.perform(get("/api/v1/players").param("position", "MID").param("q", "_mid"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("A_mid"))
				.andExpect(jsonPath("$.content[0].rank").value(1))
				.andExpect(jsonPath("$.content[0].grade").isNotEmpty())
				.andExpect(jsonPath("$.content[1].name").value("B_mid"));
	}

	@Test
	@DisplayName("같은 CSV를 다시 넣으면 이미 있는 경기는 건너뛰고, replace면 지우고 다시 넣는다")
	void reloadIsIdempotent() throws Exception {
		Path file = season().write(dir);
		loader.load(file, false);

		LckCsvLoader.Result again = loader.load(file, false);
		assertThat(again.skippedExisting()).isEqualTo(12);
		assertThat(again.loadedGames()).isZero();
		assertThat(count("lck_match")).isEqualTo(4);
		assertThat(count("player_set")).isEqualTo(120);

		LckCsvLoader.Result replaced = loader.load(file, true);
		assertThat(replaced.loadedGames()).isEqualTo(12);
		assertThat(count("lck_match")).isEqualTo(4);
		assertThat(count("stage")).isEqualTo(2);
		assertThat(count("player_set")).isEqualTo(120);
		assertThat(count("player WHERE nickname LIKE 'A\\_%' OR nickname LIKE 'B\\_%'")).isEqualTo(10);
	}

	@Test
	@DisplayName("사전 검증 오류가 있으면 아무것도 적재하지 않는다 (선수 행 누락, 매핑 없는 팀)")
	void rejectsInvalidCsvWithoutWriting() throws Exception {
		int teamsBefore = count("team");
		Path missingPlayer = new OeCsvBuilder()
				.game("2026-04-01 08:00:00", 1, REGULAR, false, "Team A", "Team B", true)
				.removeLast("mid", "Red").write(dir);
		assertThatThrownBy(() -> loader.load(missingPlayer, false))
				.isInstanceOf(LoadValidationException.class)
				.hasMessageContaining("경기 행이 11개");

		Path unknownTeam = new OeCsvBuilder()
				.game("2026-04-01 08:00:00", 1, REGULAR, false, "Team A", "Team Z", true).write(dir);
		assertThatThrownBy(() -> loader.load(unknownTeam, false))
				.isInstanceOf(LoadValidationException.class)
				.hasMessageContaining("Team Z");
		assertThat(count("team")).isEqualTo(teamsBefore);
		assertThat(count("match_set WHERE game_id LIKE 'TEST-%'")).isZero();
	}

	@Test
	@DisplayName("필요한 컬럼이 없으면 컬럼 이름을 알려 준다")
	void rejectsMissingColumns() throws Exception {
		Path file = dir.resolve("broken.csv");
		java.nio.file.Files.writeString(file, "gameid,league\nX,LCK\n");
		assertThatThrownBy(() -> loader.load(file, false))
				.isInstanceOf(LoadValidationException.class)
				.hasMessageContaining("필요한 컬럼이 없습니다")
				.hasMessageContaining("datacompleteness");
	}

	/** 정규 3시리즈(8세트) + 플레이오프 1시리즈(4세트) + 제외 대상 4경기 */
	private static OeCsvBuilder season() {
		OeCsvBuilder csv = new OeCsvBuilder();
		series(csv, "2026-04-01", REGULAR, false, new boolean[]{true, false, true});
		series(csv, "2026-04-02", REGULAR, false, new boolean[]{false, false});
		series(csv, "2026-04-03", REGULAR, false, new boolean[]{true, false, true});
		series(csv, "2026-04-10", REGULAR, true, new boolean[]{true, false, true, true});
		csv.game("LCK CL", "2026-04-04 08:00:00", 1, REGULAR, false, "Team A", "Team B", true, row -> { });
		csv.game("LCK", "2026-04-05 08:00:00", 1, REGULAR, false, "Team A", "Team B", true,
				row -> row.put("datacompleteness", "partial"));
		csv.game("2025-04-05 08:00:00", 1, REGULAR, false, "Team A", "Team B", true);
		csv.game("2026-01-20 08:00:00", 1, "Cup", false, "Team A", "Team B", true);
		return csv;
	}

	/** aWins[i]: i번째 세트를 Team A가 이기는지. A의 MID는 항상 지표가 좋다. */
	private static void series(OeCsvBuilder csv, String date, String split, boolean playoffs, boolean[] aWins) {
		for (int i = 0; i < aWins.length; i++) {
			csv.game("LCK", date + " 08:" + (10 + i * 10) + ":00", i + 1, split, playoffs, "Team A", "Team B",
					aWins[i], row -> {
						if ("A_mid".equals(row.get("playername"))) {
							row.put("kills", "9");
							row.put("damagetochampions", "30000");
							row.put("goldat15", "7000");
						}
					});
		}
	}

	private Map<String, Object> season(String nickname) {
		return jdbc.queryForMap("""
				SELECT s.total_points, s.rank_in_position FROM player_season s JOIN player p ON p.player_id = s.player_id
				WHERE p.nickname = ?""", nickname);
	}

	private int count(String from) {
		Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + from, Integer.class);
		return value == null ? 0 : value;
	}
}
