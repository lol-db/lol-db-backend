package com.riftdata.batch;

import com.riftdata.batch.CsvTable.Row;
import com.riftdata.batch.OeGame.StageKey;
import com.riftdata.batch.OeGame.TeamSide;
import com.riftdata.batch.OeGameReader.SeriesKey;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle's Elixir CSV 적재 (인계서 BR-001, BR-002, BR-159~BR-164).
 *
 * <ol>
 *   <li>사전 검증 (OeGameReader). 오류가 있으면 아무것도 쓰지 않는다.</li>
 *   <li>한 트랜잭션으로 팀·챔피언·선수·스테이지·경기·세트·팀세트·선수세트·밴을 넣고, 이어서 재계산(SeasonRecalculator)까지 한다.
 *       중간에 실패하면 전체 롤백된다.</li>
 * </ol>
 * 이미 적재한 gameid는 건너뛰므로 시즌 중 새 CSV를 다시 넣어도 된다. 기존 선수의 소속 팀·주포지션은 CSV 기준으로 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LckCsvLoader {

	private final JdbcTemplate jdbc;
	private final LoaderProperties properties;
	private final SeasonRecalculator recalculator;

	public record Result(int csvGames, int skippedExisting, int loadedGames, int newMatches, int otherLeagueRows,
			int excludedSplitRows, int incompleteRows, int otherYearRows, List<String> warnings,
			SeasonRecalculator.Result recalculation) {
	}

	/** 사전 검증만 (DB에 쓰지 않음). 검증 오류가 있으면 LoadValidationException */
	public OeGameReader.Result validate(Path file) throws IOException {
		return new OeGameReader(properties).read(CsvTable.read(file));
	}

	@Transactional
	public Result load(Path file, boolean replace) throws IOException {
		OeGameReader.Result read = new OeGameReader(properties).read(CsvTable.read(file));
		List<OeGame> games = read.games();
		if (replace) {
			clearMatches();
		}
		Set<String> existing = new HashSet<>(jdbc.queryForList("SELECT game_id FROM match_set", String.class));
		List<OeGame> newGames = games.stream().filter(game -> !existing.contains(game.gameId())).toList();

		Map<String, Long> teams = upsertTeams(games);
		Map<String, Long> champions = upsertChampions(newGames);
		Map<String, Long> players = upsertPlayers(games, teams);
		Map<StageKey, Long> stages = upsertStages(games);

		int newMatches = 0;
		Map<SeriesKey, List<OeGame>> series = newGames.stream()
				.collect(Collectors.groupingBy(SeriesKey::of, LinkedHashMap::new, Collectors.toList()));
		for (List<OeGame> seriesGames : series.values()) {
			seriesGames.sort(Comparator.comparingInt(OeGame::gameNo));
			OeGame first = seriesGames.getFirst();
			Long matchId = findMatch(first, teams);
			if (matchId == null) {
				matchId = insertMatch(first, teams, stages);
				newMatches++;
			}
			for (OeGame game : seriesGames) {
				insertGame(matchId, game, teams, players, champions);
			}
			updateBestOf(matchId);
		}
		SeasonRecalculator.Result recalculation = recalculator.recalculate();
		Result result = new Result(games.size(), games.size() - newGames.size(), newGames.size(), newMatches,
				read.otherLeagueRows(), read.excludedSplitRows(), read.incompleteRows(), read.otherYearRows(),
				read.warnings(), recalculation);
		log.info("적재 완료: {}", result);
		return result;
	}

	/** --replace: 경기 데이터만 지운다. 경기를 지우면 세트·팀세트·선수세트·밴이 CASCADE로 지워진다. */
	private void clearMatches() {
		jdbc.update("DELETE FROM player_season");
		jdbc.update("DELETE FROM lck_match");
		jdbc.update("DELETE FROM stage");
	}

	private Map<String, Long> upsertTeams(List<OeGame> games) {
		Map<String, Long> ids = new HashMap<>();
		games.stream().flatMap(game -> List.of(game.blue(), game.red()).stream()).map(TeamSide::code).distinct()
				.forEach(code -> ids.put(code, findOrInsert("SELECT team_id FROM team WHERE code = ?",
						"INSERT INTO team (code) VALUES (?)", code)));
		return ids;
	}

	private Map<String, Long> upsertChampions(List<OeGame> games) {
		Set<String> names = new LinkedHashSet<>();
		for (OeGame game : games) {
			for (TeamSide side : List.of(game.blue(), game.red())) {
				side.players().values().forEach(row -> names.add(row.text("champion")));
				for (int i = 1; i <= 5; i++) {
					String ban = side.teamRow().text("ban" + i);
					if (ban != null) names.add(ban);
				}
			}
		}
		Map<String, Long> ids = new HashMap<>();
		names.forEach(name -> ids.put(name, findOrInsert("SELECT champion_id FROM champion WHERE name = ?",
				"INSERT INTO champion (name) VALUES (?)", name)));
		return ids;
	}

	/**
	 * 선수는 닉네임으로 찾는다. 소속 팀은 마지막 경기의 팀, 주포지션은 가장 많이 뛴 포지션(같으면 최근 포지션)으로
	 * 새로 넣거나 갱신한다 (명세서: 선수 C, U(팀ID)).
	 */
	private Map<String, Long> upsertPlayers(List<OeGame> games, Map<String, Long> teams) {
		record Appearance(OeGame game, String code, Position position) {
		}
		Map<String, List<Appearance>> byName = new LinkedHashMap<>();
		for (OeGame game : games) {
			for (TeamSide side : List.of(game.blue(), game.red())) {
				side.players().forEach((position, row) -> byName.computeIfAbsent(row.text("playername"),
						name -> new ArrayList<>()).add(new Appearance(game, side.code(), position)));
			}
		}
		Map<String, Long> ids = new HashMap<>();
		byName.forEach((name, appearances) -> {
			appearances.sort(Comparator.comparing((Appearance a) -> a.game().playedAt()));
			Appearance latest = appearances.getLast();
			Map<Position, Long> counts = appearances.stream()
					.collect(Collectors.groupingBy(Appearance::position, Collectors.counting()));
			long most = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
			Position main = counts.get(latest.position()) == most ? latest.position()
					: counts.entrySet().stream().filter(entry -> entry.getValue() == most)
							.map(Map.Entry::getKey).sorted().findFirst().orElseThrow();
			Long teamId = teams.get(latest.code());
			List<Long> found = jdbc.queryForList("SELECT player_id FROM player WHERE nickname = ?", Long.class, name);
			if (found.isEmpty()) {
				ids.put(name, insert("INSERT INTO player (team_id, nickname, position) VALUES (?, ?, ?)",
						teamId, name, main.name()));
			} else {
				jdbc.update("UPDATE player SET team_id = ?, position = ? WHERE player_id = ?", teamId, main.name(),
						found.getFirst());
				ids.put(name, found.getFirst());
			}
		});
		return ids;
	}

	/** 스테이지는 이름으로 찾고 기간을 CSV 경기 날짜로 갱신한다. 새 스테이지의 진행순서는 시작일 순으로 뒤에 붙인다. */
	private Map<StageKey, Long> upsertStages(List<OeGame> games) {
		Map<StageKey, List<LocalDate>> dates = new LinkedHashMap<>();
		games.forEach(game -> dates.computeIfAbsent(game.stage(), key -> new ArrayList<>())
				.add(game.playedAt().toLocalDate()));
		Map<StageKey, Long> ids = new HashMap<>();
		Integer maxOrder = jdbc.queryForObject("SELECT COALESCE(MAX(stage_order), 0) FROM stage", Integer.class);
		int nextOrder = maxOrder == null ? 1 : maxOrder + 1;
		List<Map.Entry<StageKey, List<LocalDate>>> ordered = dates.entrySet().stream()
				.sorted(Comparator.comparing(entry -> entry.getValue().stream().min(LocalDate::compareTo).orElseThrow()))
				.toList();
		for (Map.Entry<StageKey, List<LocalDate>> entry : ordered) {
			StageKey key = entry.getKey();
			LocalDate start = entry.getValue().stream().min(LocalDate::compareTo).orElseThrow();
			LocalDate end = entry.getValue().stream().max(LocalDate::compareTo).orElseThrow();
			List<Long> found = jdbc.queryForList("SELECT stage_id FROM stage WHERE name = ?", Long.class, key.name());
			if (found.isEmpty()) {
				ids.put(key, insert("INSERT INTO stage (name, stage_order, start_date, end_date, is_regular) VALUES (?, ?, ?, ?, ?)",
						key.name(), nextOrder++, start, end, !key.playoffs()));
			} else {
				jdbc.update("UPDATE stage SET start_date = LEAST(start_date, ?), end_date = GREATEST(end_date, ?) WHERE stage_id = ?",
						start, end, found.getFirst());
				ids.put(key, found.getFirst());
			}
		}
		return ids;
	}

	/** 같은 날(한국 시간) 같은 두 팀의 경기가 이미 있으면 그 경기에 세트를 붙인다. */
	private Long findMatch(OeGame game, Map<String, Long> teams) {
		Long a = teams.get(game.blue().code());
		Long b = teams.get(game.red().code());
		List<Long> found = jdbc.queryForList("""
				SELECT match_id FROM lck_match
				WHERE DATE(match_time) = ? AND ((team1_id = ? AND team2_id = ?) OR (team1_id = ? AND team2_id = ?))
				""", Long.class, game.playedAt().toLocalDate(), a, b, b, a);
		return found.isEmpty() ? null : found.getFirst();
	}

	/**
	 * 팀1 = 시리즈 첫 세트의 블루 팀. 세트 수(Bo3·Bo5)는 세트를 넣은 뒤 updateBestOf에서 정한다.
	 * 경기 일시는 LocalDateTime 그대로 넘긴다. java.sql.Timestamp로 바꾸면 JVM 시간대(CI는 UTC)에 따라 시각이 밀린다.
	 */
	private Long insertMatch(OeGame first, Map<String, Long> teams, Map<StageKey, Long> stages) {
		return insert("INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id) VALUES (?, 3, ?, ?, ?)",
				first.playedAt(), teams.get(first.blue().code()), teams.get(first.red().code()),
				stages.get(first.stage()));
	}

	/** 원천에 시리즈 형식이 없어 승리 세트 수로 정한다: 한 팀이라도 3승이면 Bo5, 아니면 Bo3 */
	private void updateBestOf(Long matchId) {
		Integer maxWins = jdbc.queryForObject("""
				SELECT COALESCE(MAX(wins), 0) FROM (
				  SELECT COUNT(*) AS wins FROM team_set ts JOIN match_set ms ON ms.set_id = ts.set_id
				  WHERE ms.match_id = ? AND ts.victory = 1 GROUP BY ts.team_id) w
				""", Integer.class, matchId);
		jdbc.update("UPDATE lck_match SET sets = ? WHERE match_id = ?", maxWins != null && maxWins >= 3 ? 5 : 3, matchId);
	}

	private void insertGame(Long matchId, OeGame game, Map<String, Long> teams, Map<String, Long> players,
			Map<String, Long> champions) {
		Long setId = insert("INSERT INTO match_set (match_id, set_number, game_id, version, play_time) VALUES (?, ?, ?, ?, ?)",
				matchId, game.gameNo(), game.gameId(), game.patch(), LocalTime.ofSecondOfDay(game.lengthSec()));
		for (Side side : Side.values()) {
			TeamSide team = game.side(side);
			Long teamSetId = insertTeamSet(setId, teams.get(team.code()), team);
			insertPlayers(teamSetId, team, players, champions);
			insertBans(teamSetId, team.teamRow(), champions);
		}
	}

	private Long insertTeamSet(Long setId, Long teamId, TeamSide team) {
		Row row = team.teamRow();
		return insert("""
				INSERT INTO team_set (set_id, team_id, side, victory, first_blood, first_dragon, first_herald, first_baron,
				  first_tower, first_mid_tower, dragons, fire_dragons, water_dragons, earth_dragons, wind_dragons,
				  hextech_dragons, chemtech_dragons, void_grubs, barons, towers, tower_plates, inhibitors)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", setId, teamId, team.side().name(), team.win(),
				flag(row, "firstblood"), flag(row, "firstdragon"), flag(row, "firstherald"), flag(row, "firstbaron"),
				flag(row, "firsttower"), flag(row, "firstmidtower"),
				count(row, "dragons"), count(row, "infernals"), count(row, "oceans"), count(row, "mountains"),
				count(row, "clouds"), count(row, "hextechs"), count(row, "chemtechs"), count(row, "void_grubs"),
				count(row, "barons"), count(row, "towers"), count(row, "turretplates"), count(row, "inhibitors"));
	}

	private void insertPlayers(Long teamSetId, TeamSide team, Map<String, Long> players, Map<String, Long> champions) {
		List<Object[]> rows = new ArrayList<>();
		team.players().forEach((position, row) -> {
			List<Object> values = new ArrayList<>(List.of(teamSetId, position.name(),
					players.get(row.text("playername")), champions.get(row.text("champion"))));
			for (String column : PLAYER_COUNTS) values.add(count(row, column));
			for (String column : PLAYER_FLAGS) values.add(flag(row, column));
			for (String column : PLAYER_NULLABLE) values.add(row.integer(column));
			rows.add(values.toArray());
		});
		jdbc.batchUpdate(PLAYER_INSERT, rows);
	}

	private void insertBans(Long teamSetId, Row teamRow, Map<String, Long> champions) {
		Set<String> banned = new HashSet<>();
		List<Object[]> rows = new ArrayList<>();
		for (int order = 1; order <= 5; order++) {
			String champion = teamRow.text("ban" + order);
			if (champion != null && banned.add(champion)) {
				rows.add(new Object[]{teamSetId, order, champions.get(champion)});
			}
		}
		jdbc.batchUpdate("INSERT INTO ban (team_set_id, ban_order, champion_id) VALUES (?, ?, ?)", rows);
	}

	/** 기본값 0인 원천 수치. 빈 값은 0 */
	private static final List<String> PLAYER_COUNTS = List.of(
			"kills", "deaths", "assists", "doublekills", "triplekills", "quadrakills", "pentakills",
			"damagetochampions", "visionscore", "wardsplaced", "wardskilled", "controlwardsbought",
			"totalgold", "earnedgold", "goldspent", "minionkills", "monsterkills");
	private static final List<String> PLAYER_FLAGS = List.of("firstbloodkill", "firstbloodassist", "firstbloodvictim");
	/** 결측이면 NULL로 두는 원천 수치 (BR-005) */
	private static final List<String> PLAYER_NULLABLE = List.of(
			"damagetakenperminute", "damagemitigatedperminute", "damagetotowers",
			"goldat10", "xpat10", "csat10", "killsat10", "deathsat10", "assistsat10",
			"goldat15", "xpat15", "csat15", "killsat15", "deathsat15", "assistsat15",
			"goldat20", "xpat20", "csat20", "killsat20", "deathsat20", "assistsat20",
			"goldat25", "xpat25", "csat25", "killsat25", "deathsat25", "assistsat25");
	private static final String PLAYER_INSERT = """
			INSERT INTO player_set (team_set_id, position, player_id, champion_id,
			  kills, deaths, assists, double_kills, triple_kills, quadra_kills, penta_kills,
			  damage_to_champions, vision_points, wards_placed, wards_removed, control_wards_bought,
			  total_golds, earned_golds, gold_spent, minion_kills, monster_kills,
			  first_blood_kill, first_blood_assist, first_blood_death,
			  damage_taken_pm, damage_mitigated_pm, damage_to_buildings,
			  gold_at_10, xp_at_10, cs_at_10, kills_at_10, deaths_at_10, assists_at_10,
			  gold_at_15, xp_at_15, cs_at_15, kills_at_15, deaths_at_15, assists_at_15,
			  gold_at_20, xp_at_20, cs_at_20, kills_at_20, deaths_at_20, assists_at_20,
			  gold_at_25, xp_at_25, cs_at_25, kills_at_25, deaths_at_25, assists_at_25)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
			        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static int count(Row row, String column) {
		Integer value = row.integer(column);
		return value == null ? 0 : Math.max(0, value);
	}

	private static boolean flag(Row row, String column) {
		return Boolean.TRUE.equals(row.flag(column));
	}

	private Long findOrInsert(String select, String insert, String value) {
		List<Long> found = jdbc.queryForList(select, Long.class, value);
		return found.isEmpty() ? insert(insert, value) : found.getFirst();
	}

	private Long insert(String sql, Object... args) {
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
			return statement;
		}, keys);
		return Objects.requireNonNull(keys.getKey()).longValue();
	}
}
