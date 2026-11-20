package com.riftdata.batch;

import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.TeamTotals;
import com.riftdata.domain.player.service.PlayerArea;
import com.riftdata.domain.player.service.PlayerMetric;
import com.riftdata.global.common.Position;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적재 뒤 유도 값 재계산 (화면설계서 v0.5 "등급·세트 평점 기준", 인계서 BR-009~BR-016).
 *
 * <ol>
 *   <li>B3 세트 점수: 세트별 지표 20개를 시즌 전체 같은 포지션(그 세트에서 뛴 포지션)의 세트 분포에서 백분위로 바꾸고,
 *       영역별 백분위 평균 → 포지션 가중 평균 = 세트 점수(0~100). player_set.total_points에 반올림해 저장한다.
 *       세트 평점(6.0 + (점수 − 50) ÷ 12.5)은 API가 조회 때 변환한다.</li>
 *   <li>POG: 세트마다 승리 팀에서 반올림 전 세트 점수가 가장 높은 선수. 같으면 킬 관여율 높은 순 → 데스 적은 순 → 선수 ID 순.</li>
 *   <li>B4 시즌 지표: 주포지션이 같고 시즌 10세트 이상인 선수끼리 지표 시즌 값의 백분위 → 영역 점수(백분위 평균) →
 *       종합 점수(포지션 가중 평균), 포지션 내 순위. player_season을 지우고 다시 넣는다. 10세트 미만은 점수 없이 세트 수만.</li>
 * </ol>
 * 지표 정의와 계산은 API와 같은 PlayerMetric을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonRecalculator {

	static final int MIN_SEASON_SETS = 10;

	private final JdbcTemplate jdbc;
	private final LoaderProperties properties;
	private final Clock clock;

	public record Result(int scoredSets, int pogs, int seasonPlayers, int gradedPlayers) {
	}

	@Transactional
	public Result recalculate() {
		LocalDateTime start = LocalDateTime.of(properties.getSeasonYear(), 1, 1, 0, 0);
		List<SeasonSet> sets = jdbc.query(SEASON_SETS_SQL, SeasonRecalculator::seasonSet,
				start, start.plusYears(1));
		Map<Long, TeamTotals> teamTotals = teamTotals();

		Map<Long, Double> setScores = setScores(sets, teamTotals);
		Set<Long> pogs = pogs(sets, setScores, teamTotals);
		jdbc.batchUpdate("UPDATE player_set SET total_points = ?, is_pog = ? WHERE player_set_id = ?",
				sets.stream().map(set -> new Object[]{toPoints(setScores.get(set.playerSetId())),
						pogs.contains(set.playerSetId()), set.playerSetId()}).toList());

		int[] seasonCounts = seasonMetrics(sets, teamTotals);
		Result result = new Result((int) setScores.values().stream().filter(Objects::nonNull).count(),
				pogs.size(), seasonCounts[0], seasonCounts[1]);
		log.info("재계산 완료: 세트 점수 {}개, POG {}개, 시즌 지표 {}명 (등급 대상 {}명)",
				result.scoredSets(), result.pogs(), result.seasonPlayers(), result.gradedPlayers());
		return result;
	}

	/** B3: player_set_id → 반올림 전 세트 점수 */
	private Map<Long, Double> setScores(List<SeasonSet> sets, Map<Long, TeamTotals> teamTotals) {
		Map<Long, Double> scores = new HashMap<>();
		sets.stream().collect(Collectors.groupingBy(SeasonSet::setPosition)).forEach((position, rows) -> {
			Map<PlayerMetric, List<Double>> values = new EnumMap<>(PlayerMetric.class);
			for (PlayerMetric metric : PlayerMetric.values()) {
				values.put(metric, rows.stream().map(row -> metric.compute(List.of(row.set()), teamTotals)).toList());
			}
			Map<PlayerMetric, PercentileTable> tables = tables(values);
			for (int i = 0; i < rows.size(); i++) {
				int index = i;
				Map<PlayerMetric, Double> own = new EnumMap<>(PlayerMetric.class);
				values.forEach((metric, list) -> own.put(metric, list.get(index)));
				scores.put(rows.get(i).playerSetId(), weightedScore(position, own, tables).overall());
			}
		});
		return scores;
	}

	/** 세트마다 승리 팀 최고 세트 점수 선수 (BR-016) */
	private static Set<Long> pogs(List<SeasonSet> sets, Map<Long, Double> scores, Map<Long, TeamTotals> teamTotals) {
		Comparator<SeasonSet> best = Comparator
				.comparing((SeasonSet row) -> scores.get(row.playerSetId()), Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(row -> PlayerMetric.KP.compute(List.of(row.set()), teamTotals),
						Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparingInt(row -> row.set().deaths())
				.thenComparing(row -> row.set().playerId());
		return sets.stream().filter(row -> row.set().win())
				.collect(Collectors.groupingBy(SeasonSet::setId)).values().stream()
				.map(rows -> rows.stream().min(best).orElseThrow())
				.filter(row -> scores.get(row.playerSetId()) != null)
				.map(SeasonSet::playerSetId)
				.collect(Collectors.toSet());
	}

	/** B4: player_season을 지우고 다시 넣는다. [시즌 지표 행 수, 등급 대상 수] */
	private int[] seasonMetrics(List<SeasonSet> sets, Map<Long, TeamTotals> teamTotals) {
		jdbc.update("DELETE FROM player_season");
		Map<Long, List<SeasonSet>> byPlayer = sets.stream()
				.collect(Collectors.groupingBy(SeasonSet::playerId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, Score> scores = new HashMap<>();
		byPlayer.entrySet().stream()
				.filter(entry -> entry.getValue().size() >= MIN_SEASON_SETS)
				.collect(Collectors.groupingBy(entry -> entry.getValue().getFirst().mainPosition()))
				.forEach((position, players) -> {
					Map<PlayerMetric, List<Double>> values = new EnumMap<>(PlayerMetric.class);
					for (PlayerMetric metric : PlayerMetric.values()) {
						values.put(metric, players.stream().map(entry -> metric.compute(
								entry.getValue().stream().map(SeasonSet::set).toList(), teamTotals)).toList());
					}
					Map<PlayerMetric, PercentileTable> tables = tables(values);
					for (int i = 0; i < players.size(); i++) {
						int index = i;
						Map<PlayerMetric, Double> own = new EnumMap<>(PlayerMetric.class);
						values.forEach((metric, list) -> own.put(metric, list.get(index)));
						scores.put(players.get(i).getKey(), weightedScore(position, own, tables));
					}
				});
		Map<Long, Integer> ranks = ranks(byPlayer, scores);
		LocalDateTime now = LocalDateTime.now(clock);
		List<Object[]> rows = new ArrayList<>();
		byPlayer.forEach((playerId, playerSets) -> {
			Score score = scores.get(playerId);
			rows.add(new Object[]{playerId, playerSets.size(),
					score == null ? null : toPoints(score.overall()),
					score == null ? null : toPoints(score.area(PlayerArea.COMBAT)),
					score == null ? null : toPoints(score.area(PlayerArea.DAMAGE)),
					score == null ? null : toPoints(score.area(PlayerArea.LANE)),
					score == null ? null : toPoints(score.area(PlayerArea.GROWTH)),
					score == null ? null : toPoints(score.area(PlayerArea.VISION)),
					score == null ? null : toPoints(score.area(PlayerArea.SURVIVAL)),
					ranks.get(playerId), now});
		});
		jdbc.batchUpdate("""
				INSERT INTO player_season (player_id, sets, total_points, combat_points, damage_points, laning_points,
				                           growth_points, ward_points, survival_points, rank_in_position, calculated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", rows);
		return new int[]{rows.size(), (int) scores.values().stream().filter(score -> score.overall() != null).count()};
	}

	/** 주포지션 안에서 종합 점수 내림차순 순위. 같은 점수는 같은 순위 (1, 1, 3) */
	private static Map<Long, Integer> ranks(Map<Long, List<SeasonSet>> byPlayer, Map<Long, Score> scores) {
		Map<Long, Integer> ranks = new HashMap<>();
		scores.entrySet().stream()
				.filter(entry -> entry.getValue().overall() != null)
				.collect(Collectors.groupingBy(entry -> byPlayer.get(entry.getKey()).getFirst().mainPosition()))
				.values().forEach(group -> group.forEach(entry -> {
					double own = Math.round(entry.getValue().overall() * 1e8) / 1e8;
					long better = group.stream()
							.filter(other -> Math.round(other.getValue().overall() * 1e8) / 1e8 > own).count();
					ranks.put(entry.getKey(), (int) better + 1);
				}));
		return ranks;
	}

	private static Map<PlayerMetric, PercentileTable> tables(Map<PlayerMetric, List<Double>> values) {
		Map<PlayerMetric, PercentileTable> tables = new EnumMap<>(PlayerMetric.class);
		values.forEach((metric, list) -> tables.put(metric, new PercentileTable(list, metric.direction())));
		return tables;
	}

	/** 영역 점수 = 영역 지표 백분위 평균, 종합 = 값이 있는 영역의 포지션 가중 평균 (BR-012, BR-013) */
	static Score weightedScore(Position position, Map<PlayerMetric, Double> values,
			Map<PlayerMetric, PercentileTable> tables) {
		Map<PlayerArea, Double> areas = new EnumMap<>(PlayerArea.class);
		double weighted = 0;
		double weights = 0;
		for (PlayerArea area : PlayerArea.values()) {
			double sum = 0;
			int count = 0;
			for (PlayerMetric metric : PlayerMetric.of(area)) {
				Double percentile = tables.get(metric).percentile(values.get(metric));
				if (percentile != null) {
					sum += percentile;
					count++;
				}
			}
			if (count > 0) {
				double score = sum / count;
				areas.put(area, score);
				weighted += score * area.weight(position);
				weights += area.weight(position);
			}
		}
		return new Score(weights == 0 ? null : weighted / weights, areas);
	}

	record Score(Double overall, Map<PlayerArea, Double> areas) {
		Double area(PlayerArea area) {
			return areas.get(area);
		}
	}

	private static Integer toPoints(Double score) {
		return score == null ? null : (int) Math.max(0, Math.min(100, Math.round(score)));
	}

	private Map<Long, TeamTotals> teamTotals() {
		Map<Long, TeamTotals> totals = new HashMap<>();
		jdbc.query("""
				SELECT team_set_id, SUM(kills) kills, SUM(damage_to_champions) damage, SUM(earned_golds) earned
				FROM player_set GROUP BY team_set_id
				""", (ResultSet rs) -> {
			totals.put(rs.getLong("team_set_id"),
					new TeamTotals(rs.getLong("kills"), rs.getLong("damage"), rs.getLong("earned")));
		});
		return totals;
	}

	/** 한 선수의 한 세트 + 재계산에 필요한 키 */
	record SeasonSet(long playerSetId, long playerId, long setId, Position mainPosition, Position setPosition,
			OverviewSet set) {
	}

	/** 상대 라이너 = 같은 세트, 상대 팀, 같은 포지션. UNSIGNED 컬럼끼리 빼면 음수에서 오류가 나므로 SIGNED로 바꿔 뺀다. */
	private static final String SEASON_SETS_SQL = """
			SELECT ps.player_set_id, ps.player_id, p.position AS main_position, ps.position AS set_position,
			       ts.team_set_id, ts.set_id, m.match_id, m.match_time, ms.set_number,
			       TIME_TO_SEC(ms.play_time) AS duration_sec, ps.champion_id, c.name AS champion_name, ts.victory,
			       ps.kills, ps.deaths, ps.assists, ps.minion_kills + ps.monster_kills AS cs,
			       ps.damage_to_champions, ps.earned_golds, ps.vision_points,
			       CAST(ps.gold_at_15 AS SIGNED) - CAST(e.gold_at_15 AS SIGNED) AS gold_diff_15,
			       CAST(ps.cs_at_15 AS SIGNED) - CAST(e.cs_at_15 AS SIGNED) AS cs_diff_15,
			       CAST(ps.xp_at_15 AS SIGNED) - CAST(e.xp_at_15 AS SIGNED) AS xp_diff_15,
			       ps.first_blood_kill, ps.first_blood_assist, ps.first_blood_death,
			       ps.kills_at_15, ps.assists_at_15, ps.deaths_at_15,
			       ps.wards_placed, ps.wards_removed, ps.control_wards_bought, e.player_id AS enemy_player_id
			FROM player_set ps
			JOIN player p ON p.player_id = ps.player_id
			JOIN champion c ON c.champion_id = ps.champion_id
			JOIN team_set ts ON ts.team_set_id = ps.team_set_id
			JOIN match_set ms ON ms.set_id = ts.set_id
			JOIN lck_match m ON m.match_id = ms.match_id
			LEFT JOIN team_set ets ON ets.set_id = ts.set_id AND ets.team_id <> ts.team_id
			LEFT JOIN player_set e ON e.team_set_id = ets.team_set_id AND e.position = ps.position
			WHERE m.match_time >= ? AND m.match_time < ?
			ORDER BY ps.player_set_id
			""";

	private static SeasonSet seasonSet(ResultSet rs, int rowNum) throws SQLException {
		OverviewSet set = new OverviewSet(rs.getLong("player_id"), rs.getLong("team_set_id"), rs.getLong("match_id"),
				rs.getObject("match_time", LocalDateTime.class), rs.getInt("set_number"), rs.getInt("duration_sec"),
				rs.getLong("champion_id"), rs.getString("champion_name"), null, null, rs.getBoolean("victory"),
				rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("assists"), rs.getInt("cs"),
				rs.getInt("damage_to_champions"), rs.getInt("earned_golds"), rs.getInt("vision_points"),
				nullableDouble(rs, "gold_diff_15"), nullableDouble(rs, "cs_diff_15"), nullableDouble(rs, "xp_diff_15"),
				null, false, rs.getBoolean("first_blood_kill"), rs.getBoolean("first_blood_assist"),
				rs.getBoolean("first_blood_death"), nullableInt(rs, "kills_at_15"), nullableInt(rs, "assists_at_15"),
				nullableInt(rs, "deaths_at_15"), rs.getInt("wards_placed"), rs.getInt("wards_removed"),
				rs.getInt("control_wards_bought"), nullableLong(rs, "enemy_player_id"), null);
		return new SeasonSet(rs.getLong("player_set_id"), rs.getLong("player_id"), rs.getLong("set_id"),
				Position.valueOf(rs.getString("main_position")), Position.valueOf(rs.getString("set_position")), set);
	}

	private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
		double value = rs.getDouble(column);
		return rs.wasNull() ? null : value;
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
}
