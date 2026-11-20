package com.riftdata.batch;

import com.riftdata.batch.CsvTable.CsvValueException;
import com.riftdata.batch.CsvTable.Row;
import com.riftdata.batch.OeGame.StageKey;
import com.riftdata.batch.OeGame.TeamSide;
import com.riftdata.global.common.KstTime;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 원천 CSV를 경기 단위로 묶고 사전 검증한다 (DB에 쓰지 않음).
 * 오류가 하나라도 있으면 적재하지 않는다 (BR-163). 오류는 줄 번호와 함께 모아서 한 번에 알려 준다.
 */
public final class OeGameReader {

	/** 적재에 쓰는 원천 컬럼. 하나라도 없으면 파일 형식 오류다. */
	static final List<String> REQUIRED_COLUMNS = List.of(
			"gameid", "datacompleteness", "league", "date", "game", "patch", "split", "playoffs",
			"side", "position", "playername", "teamname", "champion",
			"ban1", "ban2", "ban3", "ban4", "ban5", "gamelength", "result",
			"kills", "deaths", "assists", "doublekills", "triplekills", "quadrakills", "pentakills",
			"firstblood", "firstbloodkill", "firstbloodassist", "firstbloodvictim",
			"firstdragon", "dragons", "infernals", "mountains", "clouds", "oceans", "chemtechs", "hextechs",
			"firstherald", "void_grubs", "firstbaron", "barons", "firsttower", "towers", "firstmidtower",
			"turretplates", "inhibitors",
			"damagetochampions", "damagetakenperminute", "damagemitigatedperminute", "damagetotowers",
			"wardsplaced", "wardskilled", "controlwardsbought", "visionscore",
			"totalgold", "earnedgold", "goldspent", "minionkills", "monsterkills",
			"goldat10", "xpat10", "csat10", "killsat10", "assistsat10", "deathsat10",
			"goldat15", "xpat15", "csat15", "killsat15", "assistsat15", "deathsat15",
			"goldat20", "xpat20", "csat20", "killsat20", "assistsat20", "deathsat20",
			"goldat25", "xpat25", "csat25", "killsat25", "assistsat25", "deathsat25");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final Map<String, Position> POSITIONS = Map.of(
			"top", Position.TOP, "jng", Position.JUNGLE, "mid", Position.MID, "bot", Position.ADC, "sup", Position.SUPPORT);
	private static final int MAX_ERRORS = 100;

	private final LoaderProperties properties;
	private final List<String> errors = new ArrayList<>();
	private final Set<String> unknownTeams = new LinkedHashSet<>();
	private int otherLeagueRows;
	private int excludedSplitRows;
	private int incompleteRows;
	private int otherYearRows;

	public OeGameReader(LoaderProperties properties) {
		this.properties = properties;
	}

	public record Result(List<OeGame> games, int otherLeagueRows, int excludedSplitRows, int incompleteRows,
			int otherYearRows, List<String> warnings) {
	}

	/**
	 * CSV를 읽을 때 대상 리그 행만 남기는 필터. 원천 전체(전 세계 리그)를 메모리에 올리지 않게 한다.
	 * 걸러 낸 행은 CsvTable.skippedRows()로 "다른 리그"에 센다.
	 */
	public static Predicate<Row> leagueFilter(LoaderProperties properties) {
		return row -> properties.getLeague().equalsIgnoreCase(String.valueOf(row.text("league")));
	}

	/** 검증 오류가 있으면 LoadValidationException */
	public Result read(CsvTable table) {
		otherLeagueRows = table.skippedRows();
		List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !table.has(column)).toList();
		if (!missing.isEmpty()) {
			throw new LoadValidationException(List.of("CSV에 필요한 컬럼이 없습니다: " + String.join(", ", missing)));
		}
		Map<String, List<Row>> byGame = new LinkedHashMap<>();
		for (Row row : table.rows()) {
			if (!properties.getLeague().equalsIgnoreCase(String.valueOf(row.text("league")))) {
				otherLeagueRows++;
			} else if (properties.getExcludedSplits().stream()
					.anyMatch(split -> split.equalsIgnoreCase(String.valueOf(row.text("split"))))) {
				excludedSplitRows++;
			} else if (!"complete".equalsIgnoreCase(String.valueOf(row.text("datacompleteness")))) {
				incompleteRows++;
			} else {
				LocalDateTime playedAt = playedAt(row);
				if (playedAt != null && playedAt.getYear() != properties.getSeasonYear()) {
					otherYearRows++;
				} else {
					byGame.computeIfAbsent(String.valueOf(row.text("gameid")), id -> new ArrayList<>()).add(row);
				}
			}
		}
		List<OeGame> games = new ArrayList<>();
		byGame.forEach((gameId, rows) -> {
			try {
				OeGame game = game(gameId, rows);
				if (game != null) games.add(game);
			} catch (CsvValueException e) {
				error(e.getMessage());
			}
		});
		duplicateSetNumbers(games);
		if (!unknownTeams.isEmpty()) {
			errors.add(0, "팀코드 매핑이 없는 팀: " + String.join(", ", unknownTeams)
					+ " → application-loader.yml의 loader.team-codes에 추가하세요");
		}
		if (!errors.isEmpty()) {
			throw new LoadValidationException(errors);
		}
		return new Result(games, otherLeagueRows, excludedSplitRows, incompleteRows, otherYearRows, incompleteSeries(games));
	}

	private OeGame game(String gameId, List<Row> rows) {
		int line = rows.getFirst().line();
		int before = errors.size();
		if (gameId.equals("null") || gameId.length() > 40) {
			error(line + "행: gameid가 비었거나 40자를 넘습니다");
			return null;
		}
		if (rows.size() != 12) {
			error(line + "행 " + gameId + ": 경기 행이 " + rows.size() + "개입니다 (선수 10 + 팀 2 = 12개여야 함)");
			return null;
		}
		Row first = rows.getFirst();
		LocalDateTime playedAt = playedAt(first);
		Integer gameNo = first.integer("game");
		Integer length = first.integer("gamelength");
		String patch = first.text("patch");
		if (playedAt == null) error(line + "행 " + gameId + ": date 형식 오류 (" + first.text("date") + ")");
		if (gameNo == null || gameNo < 1 || gameNo > 5) error(line + "행 " + gameId + ": game(세트 번호)는 1~5여야 합니다");
		if (length == null || length <= 0 || length >= 86_400) error(line + "행 " + gameId + ": gamelength가 올바르지 않습니다");
		if (patch == null || patch.length() > 15) error(line + "행 " + gameId + ": patch가 비었거나 15자를 넘습니다");

		TeamSide blue = side(gameId, rows, Side.BLUE);
		TeamSide red = side(gameId, rows, Side.RED);
		if (blue != null && red != null) {
			if (blue.teamName().equals(red.teamName())) error(line + "행 " + gameId + ": 양 팀이 같습니다");
			if (blue.win() == red.win()) error(line + "행 " + gameId + ": 승리 팀이 하나가 아닙니다 (result)");
		}
		if (errors.size() > before || blue == null || red == null) return null;
		Boolean playoffs = first.flag("playoffs");
		return new OeGame(gameId, line, playedAt, gameNo, patch,
				new StageKey(first.text("split"), Boolean.TRUE.equals(playoffs)), length, blue, red);
	}

	private TeamSide side(String gameId, List<Row> rows, Side side) {
		List<Row> sideRows = rows.stream()
				.filter(row -> side.name().equalsIgnoreCase(String.valueOf(row.text("side")))).toList();
		int line = rows.getFirst().line();
		Row teamRow = null;
		Map<Position, Row> players = new EnumMap<>(Position.class);
		for (Row row : sideRows) {
			String position = String.valueOf(row.text("position")).toLowerCase(Locale.ROOT);
			if (position.equals("team")) {
				teamRow = row;
				continue;
			}
			Position mapped = POSITIONS.get(position);
			if (mapped == null) {
				error(row.line() + "행 " + gameId + ": 알 수 없는 position (" + position + ")");
			} else if (players.put(mapped, row) != null) {
				error(row.line() + "행 " + gameId + ": " + side + " " + mapped + " 선수가 두 명입니다");
			}
			String name = row.text("playername");
			if (name == null || name.length() > 15) error(row.line() + "행 " + gameId + ": playername이 비었거나 15자를 넘습니다");
			String champion = row.text("champion");
			if (champion == null || champion.length() > 20) error(row.line() + "행 " + gameId + ": champion이 비었거나 20자를 넘습니다");
		}
		if (teamRow == null || players.size() != 5) {
			error(line + "행 " + gameId + ": " + side + " 팀 행 1개와 포지션 5개 선수 행이 필요합니다");
			return null;
		}
		String teamName = teamRow.text("teamname");
		if (teamName == null) {
			error(teamRow.line() + "행 " + gameId + ": teamname이 비었습니다");
			return null;
		}
		String code = properties.getTeamCodes().get(teamName);
		if (code == null) {
			unknownTeams.add(teamName);
		} else if (code.length() > 4) {
			error("팀코드는 4자 이하여야 합니다: " + teamName + " → " + code);
		}
		for (int i = 1; i <= 5; i++) {
			String ban = teamRow.text("ban" + i);
			if (ban != null && ban.length() > 20) error(teamRow.line() + "행 " + gameId + ": ban" + i + "이 20자를 넘습니다");
		}
		Integer result = teamRow.integer("result");
		return new TeamSide(side, teamName, code, result != null && result == 1, teamRow, players);
	}

	/**
	 * 원천에서 세트가 빠진 것으로 보이는 시리즈 (경고, 적재는 함): 세트 번호가 1부터 이어지지 않거나 어느 팀도 2승에 닿지 않음.
	 * 순위표는 승리 세트 수에 도달한 경기만 집계하므로 이런 경기는 순위에서 빠진다.
	 */
	private static List<String> incompleteSeries(List<OeGame> games) {
		List<String> warnings = new ArrayList<>();
		games.stream().collect(java.util.stream.Collectors.groupingBy(SeriesKey::of,
				() -> new java.util.TreeMap<>(java.util.Comparator.comparing(SeriesKey::date).thenComparing(SeriesKey::teamA)
						.thenComparing(SeriesKey::teamB)), java.util.stream.Collectors.toList())).forEach((key, series) -> {
			List<Integer> numbers = series.stream().map(OeGame::gameNo).sorted().toList();
			boolean gap = numbers.getFirst() != 1 || numbers.getLast() != numbers.size();
			long aWins = series.stream().filter(game -> game.blue().teamName().equals(key.teamA()) == game.blue().win()).count();
			long maxWins = Math.max(aWins, series.size() - aWins);
			if (gap || maxWins < 2) {
				warnings.add(key.date() + " " + key.teamA() + " vs " + key.teamB() + ": 세트 번호 " + numbers
						+ ", 승수 " + aWins + ":" + (series.size() - aWins) + " → 원천에 세트가 빠진 것으로 보임 (순위표 집계 제외)");
			}
		});
		return warnings;
	}

	/** 같은 날 같은 두 팀 시리즈 안에서 세트 번호가 겹치면 오류 */
	private void duplicateSetNumbers(List<OeGame> games) {
		Map<String, OeGame> seen = new LinkedHashMap<>();
		for (OeGame game : games) {
			String key = SeriesKey.of(game) + "#" + game.gameNo();
			OeGame other = seen.putIfAbsent(key, game);
			if (other != null) {
				error(game.line() + "행 " + game.gameId() + ": " + other.gameId() + "와 같은 시리즈·세트 번호입니다");
			}
		}
	}

	private LocalDateTime playedAt(Row row) {
		String value = row.text("date");
		if (value == null) return null;
		try {
			return LocalDateTime.parse(value, DATE).atZone(properties.getSourceZone())
					.withZoneSameInstant(KstTime.ZONE).toLocalDateTime();
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private void error(String message) {
		if (errors.size() < MAX_ERRORS) {
			errors.add(message);
		} else if (errors.size() == MAX_ERRORS) {
			errors.add("... 오류가 더 있습니다 (" + MAX_ERRORS + "개까지만 표시)");
		}
	}

	/** 시리즈(경기) 묶음 기준: 한국 날짜 + 두 팀 */
	public record SeriesKey(java.time.LocalDate date, String teamA, String teamB) {
		public static SeriesKey of(OeGame game) {
			String a = game.blue().teamName();
			String b = game.red().teamName();
			return a.compareTo(b) <= 0 ? new SeriesKey(game.playedAt().toLocalDate(), a, b)
					: new SeriesKey(game.playedAt().toLocalDate(), b, a);
		}
	}

	/** 사전 검증 실패. 아무것도 적재하지 않는다. */
	public static class LoadValidationException extends RuntimeException {
		private final List<String> errors;

		public LoadValidationException(List<String> errors) {
			super("CSV 사전 검증 실패 (" + errors.size() + "건)\n  - " + String.join("\n  - ", errors));
			this.errors = List.copyOf(errors);
		}

		public List<String> errors() {
			return errors;
		}
	}
}
