package com.riftdata.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 테스트용 Oracle's Elixir 형식 CSV. 경기마다 선수 10행 + 팀 2행을 만든다.
 * 컬럼은 OeGameReader.REQUIRED_COLUMNS 전부와 원천에 있는 몇 가지(participantid 등)다.
 */
class OeCsvBuilder {

	static final String[] POSITIONS = {"top", "jng", "mid", "bot", "sup"};

	private final List<String> header;
	private final List<Map<String, String>> rows = new ArrayList<>();
	private int gameSeq;

	OeCsvBuilder() {
		Set<String> columns = new LinkedHashSet<>(List.of("gameid", "datacompleteness", "url", "league", "year",
				"split", "playoffs", "date", "game", "patch", "participantid", "side", "position", "playername",
				"playerid", "teamname", "teamid", "champion"));
		columns.addAll(OeGameReader.REQUIRED_COLUMNS);
		header = List.copyOf(columns);
	}

	/**
	 * 경기 한 개를 추가한다.
	 *
	 * @param blueWins   블루 팀 승리 여부
	 * @param customizer 행마다 값을 바꾸는 함수 (row.get("position"), row.get("teamname") 등으로 구분)
	 */
	OeCsvBuilder game(String league, String dateUtc, int gameNo, String split, boolean playoffs, String blueTeam,
			String redTeam, boolean blueWins, Consumer<Map<String, String>> customizer) {
		String gameId = "TEST-" + (++gameSeq);
		for (String side : List.of("Blue", "Red")) {
			String team = side.equals("Blue") ? blueTeam : redTeam;
			boolean win = side.equals("Blue") == blueWins;
			String prefix = team.substring(team.length() - 1);
			for (int i = 0; i < 6; i++) {
				Map<String, String> row = new LinkedHashMap<>();
				header.forEach(column -> row.put(column, ""));
				row.put("gameid", gameId);
				row.put("datacompleteness", "complete");
				row.put("league", league);
				row.put("year", dateUtc.substring(0, 4));
				row.put("split", split);
				row.put("playoffs", playoffs ? "1" : "0");
				row.put("date", dateUtc);
				row.put("game", String.valueOf(gameNo));
				row.put("patch", "26.05");
				row.put("side", side);
				row.put("teamname", team);
				row.put("gamelength", "1800");
				row.put("result", win ? "1" : "0");
				if (i < 5) {
					String position = POSITIONS[i];
					row.put("participantid", String.valueOf((side.equals("Blue") ? 1 : 6) + i));
					row.put("position", position);
					row.put("playername", prefix + "_" + position);
					row.put("champion", "Champ_" + prefix + "_" + position);
					fillPlayer(row, win);
				} else {
					row.put("participantid", side.equals("Blue") ? "100" : "200");
					row.put("position", "team");
					row.put("playername", "");
					row.put("champion", "");
					for (int b = 1; b <= 5; b++) row.put("ban" + b, "Ban_" + prefix + "_" + b);
					fillTeam(row, win);
				}
				customizer.accept(row);
				rows.add(row);
			}
		}
		return this;
	}

	OeCsvBuilder game(String dateUtc, int gameNo, String split, boolean playoffs, String blue, String red,
			boolean blueWins) {
		return game("LCK", dateUtc, gameNo, split, playoffs, blue, red, blueWins, row -> { });
	}

	/** 마지막으로 추가한 행들 중 조건에 맞는 행을 지운다 (검증 오류 테스트용) */
	OeCsvBuilder removeLast(String position, String side) {
		for (int i = rows.size() - 1; i >= 0; i--) {
			Map<String, String> row = rows.get(i);
			if (row.get("position").equals(position) && row.get("side").equals(side)) {
				rows.remove(i);
				return this;
			}
		}
		throw new IllegalStateException("행이 없습니다");
	}

	Path write(Path dir) throws IOException {
		StringBuilder csv = new StringBuilder(String.join(",", header)).append("\r\n");
		for (Map<String, String> row : rows) {
			List<String> values = new ArrayList<>();
			for (String column : header) {
				String value = row.get(column);
				values.add(value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value);
			}
			csv.append(String.join(",", values)).append("\r\n");
		}
		Path file = dir.resolve("oe-test-" + System.nanoTime() + ".csv");
		Files.writeString(file, csv.toString());
		return file;
	}

	private static void fillPlayer(Map<String, String> row, boolean win) {
		row.put("kills", win ? "4" : "1");
		row.put("deaths", win ? "1" : "3");
		row.put("assists", win ? "6" : "2");
		for (String column : List.of("doublekills", "triplekills", "quadrakills", "pentakills")) row.put(column, "0");
		row.put("firstbloodkill", "0");
		row.put("firstbloodassist", "0");
		row.put("firstbloodvictim", "0");
		row.put("damagetochampions", win ? "18000" : "12000");
		row.put("damagetakenperminute", "612.4");
		row.put("damagemitigatedperminute", "300");
		row.put("damagetotowers", "2000");
		row.put("wardsplaced", "12");
		row.put("wardskilled", "5");
		row.put("controlwardsbought", "3");
		row.put("visionscore", "40");
		row.put("totalgold", "13000");
		row.put("earnedgold", win ? "9000" : "8000");
		row.put("goldspent", "12500");
		row.put("minionkills", "250");
		row.put("monsterkills", "10");
		for (int minute : new int[]{10, 15, 20, 25}) {
			row.put("goldat" + minute, String.valueOf(minute * (win ? 400 : 380)));
			row.put("xpat" + minute, String.valueOf(minute * 500));
			row.put("csat" + minute, String.valueOf(minute * 8));
			row.put("killsat" + minute, "1");
			row.put("assistsat" + minute, "1");
			row.put("deathsat" + minute, "0");
		}
	}

	private static void fillTeam(Map<String, String> row, boolean win) {
		row.put("kills", win ? "20" : "5");
		row.put("deaths", win ? "5" : "20");
		for (String column : List.of("firstblood", "firstdragon", "firstherald", "firstbaron", "firsttower", "firstmidtower")) {
			row.put(column, win ? "1" : "0");
		}
		row.put("dragons", win ? "4" : "1");
		row.put("infernals", win ? "2" : "0");
		row.put("mountains", win ? "1" : "1");
		row.put("clouds", win ? "1" : "0");
		row.put("oceans", "0");
		row.put("chemtechs", "0");
		row.put("hextechs", "0");
		row.put("void_grubs", win ? "4" : "2");
		row.put("barons", win ? "1" : "0");
		row.put("towers", win ? "10" : "3");
		row.put("turretplates", "5");
		row.put("inhibitors", win ? "2" : "0");
	}
}
