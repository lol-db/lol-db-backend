package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerAreasResponse;
import com.riftdata.domain.player.dto.PlayerAreasResponse.AreaCard;
import com.riftdata.domain.player.dto.PlayerAreasResponse.AreaMetric;
import com.riftdata.domain.player.dto.PlayerAreasResponse.ChampionDamage;
import com.riftdata.domain.player.dto.PlayerAreasResponse.CombatExtra;
import com.riftdata.domain.player.dto.PlayerAreasResponse.CombatPoint;
import com.riftdata.domain.player.dto.PlayerAreasResponse.Extra;
import com.riftdata.domain.player.dto.PlayerAreasResponse.KillParticipation;
import com.riftdata.domain.player.dto.PlayerAreasResponse.LaneExtra;
import com.riftdata.domain.player.dto.PlayerAreasResponse.LaneMatchup;
import com.riftdata.domain.player.dto.PlayerAreasResponse.LanePoint;
import com.riftdata.domain.player.dto.PlayerAreasResponse.SetPoint;
import com.riftdata.domain.player.dto.PlayerAreasResponse.VisionExtra;
import com.riftdata.domain.player.dto.PlayerAreasResponse.VisionPoint;
import com.riftdata.domain.player.dto.PlayerAreasResponse.WardItem;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.repository.PlayerOverviewRepository;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.OverviewSet;
import com.riftdata.domain.player.repository.PlayerOverviewRepository.TeamTotals;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선수 영역 탭 (API-16). 탭 3개를 group 하나로 처리한다 (명세서 결정 11-3).
 * 영역 점수는 저장된 시즌 점수, 지표는 원천 기록으로 계산하며 비교 집단은 종합 탭과 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerAreasService {

	private static final Comparator<OverviewSet> CHRONOLOGICAL = Comparator
			.comparing(OverviewSet::playedAt).thenComparing(OverviewSet::matchId).thenComparingInt(OverviewSet::setNo);

	private final PlayerRepository playerRepository;
	private final PlayerSeasonRepository playerSeasonRepository;
	private final PlayerOverviewRepository overviewRepository;

	/** 탭 그룹과 영역. 화면 탭 이름 순서(전투·딜량 / 라인전·성장 / 생존·시야)를 따른다. */
	enum Group {
		COMBAT(PlayerArea.COMBAT, PlayerArea.DAMAGE),
		LANE(PlayerArea.LANE, PlayerArea.GROWTH),
		VISION(PlayerArea.SURVIVAL, PlayerArea.VISION);

		private final List<PlayerArea> areas;

		Group(PlayerArea... areas) {
			this.areas = List.of(areas);
		}

		static Group parse(String value) {
			if (value == null || value.isBlank()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			}
			return switch (value.strip().toLowerCase(Locale.ROOT)) {
				case "combat" -> COMBAT;
				case "lane" -> LANE;
				case "vision" -> VISION;
				default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
			};
		}
	}

	public PlayerAreasResponse getAreas(Long playerId, String group, String patch, Long championId) {
		Group tab = Group.parse(group);
		String normalizedPatch = PlayerQuerySupport.normalizePatch(patch);
		PlayerQuerySupport.validateChampionId(championId);
		Player player = PlayerQuerySupport.requirePlayer(playerRepository, playerId);
		PositionComparison comparison = PositionComparison.load(overviewRepository, player, normalizedPatch, championId);
		PlayerSeason season = comparison.seasonScoresVisible()
				? playerSeasonRepository.findByPlayerId(playerId)
						.filter(saved -> saved.getSets() >= PositionComparison.MIN_SETS).orElse(null)
				: null;

		List<AreaCard> cards = tab.areas.stream().map(area -> areaCard(area, season, comparison)).toList();
		List<OverviewSet> own = comparison.own().stream().sorted(CHRONOLOGICAL).toList();
		List<SetPoint> series = own.stream().map(row -> setPoint(tab, row)).toList();
		Extra extra = switch (tab) {
			case COMBAT -> combatExtra(own, comparison.teamTotals());
			case LANE -> laneExtra(own);
			case VISION -> visionExtra(own, comparison.comparable());
		};
		return new PlayerAreasResponse(tab.name().toLowerCase(Locale.ROOT), cards, series, extra);
	}

	private static AreaCard areaCard(PlayerArea area, PlayerSeason season, PositionComparison comparison) {
		Integer points = area.points(season);
		List<AreaMetric> metrics = comparison.metrics(PlayerMetric.of(area)).values().stream()
				.map(view -> new AreaMetric(view.metric().name(), view.metric().label(), view.value(),
						view.positionAvg(), view.topPercent(), view.grade(), view.metric().direction()))
				.toList();
		return new AreaCard(area, area.label(), points == null ? null : points.doubleValue(),
				PlayerGrade.fromPoints(points), points == null ? null : 100.0 - points, metrics);
	}

	private static SetPoint setPoint(Group tab, OverviewSet row) {
		return switch (tab) {
			case COMBAT -> new CombatPoint(row.matchId(), row.setNo(), row.win(),
					perMinute(row.damage(), row.durationSec()), row.kills(), row.assists());
			case LANE -> new LanePoint(row.matchId(), row.setNo(), row.win(),
					Decimals.stat(row.goldDiff15()), perMinute(row.cs(), row.durationSec()));
			case VISION -> new VisionPoint(row.matchId(), row.setNo(), row.win(), row.deaths(),
					perMinute(row.vision(), row.durationSec()));
		};
	}

	private static Double perMinute(int value, int durationSec) {
		return durationSec == 0 ? null : Decimals.stat(value * 60.0 / durationSec);
	}

	private static CombatExtra combatExtra(List<OverviewSet> own, Map<Long, TeamTotals> teamTotals) {
		int kills = own.stream().mapToInt(OverviewSet::kills).sum();
		int assists = own.stream().mapToInt(OverviewSet::assists).sum();
		long teamKills = own.stream().map(OverviewSet::teamSetId).distinct()
				.mapToLong(id -> teamTotals.getOrDefault(id, TeamTotals.EMPTY).kills()).sum();
		List<ChampionDamage> damage = own.stream()
				.collect(Collectors.groupingBy(OverviewSet::championId)).values().stream()
				.map(rows -> new ChampionDamage(rows.getFirst().championId(), rows.getFirst().championName(),
						rows.size(), PlayerMetric.DPM.round(PlayerMetric.DPM.compute(rows, Map.of()))))
				.sorted(Comparator.comparing(ChampionDamage::dpm, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(ChampionDamage::championId))
				.toList();
		return new CombatExtra(new KillParticipation(kills, assists,
				Math.max(0, teamKills - kills - assists), teamKills), damage);
	}

	private static LaneExtra laneExtra(List<OverviewSet> own) {
		List<LaneMatchup> matchups = own.stream()
				.filter(row -> row.enemyPlayerId() != null)
				.collect(Collectors.groupingBy(OverviewSet::enemyPlayerId)).values().stream()
				.map(rows -> {
					OverviewSet latest = rows.stream().max(CHRONOLOGICAL).orElseThrow();
					int wins = (int) rows.stream().filter(OverviewSet::win).count();
					return new LaneMatchup(latest.enemyPlayerId(), latest.enemyName(), latest.opponentCode(),
							rows.size(), wins, rows.size() - wins,
							PlayerMetric.GD15.round(PlayerMetric.GD15.compute(rows, Map.of())),
							PlayerMetric.CSD15.round(PlayerMetric.CSD15.compute(rows, Map.of())),
							PlayerMetric.XPD15.round(PlayerMetric.XPD15.compute(rows, Map.of())),
							Rating.average(rows.stream().map(row -> Rating.fromPoints(row.totalPoints())).toList()));
				})
				.sorted(Comparator.comparingInt(LaneMatchup::sets).reversed().thenComparing(LaneMatchup::name))
				.toList();
		return new LaneExtra(matchups);
	}

	private static VisionExtra visionExtra(List<OverviewSet> own, Map<Long, List<OverviewSet>> comparable) {
		return new VisionExtra(List.of(
				ward("WARDS_PLACED", "와드 설치", OverviewSet::wardsPlaced, own, comparable),
				ward("WARDS_REMOVED", "와드 제거", OverviewSet::wardsRemoved, own, comparable),
				ward("CONTROL_WARDS_BOUGHT", "제어 와드 구매", OverviewSet::controlWards, own, comparable),
				ward("VISION_SCORE", "시야 점수", OverviewSet::vision, own, comparable)));
	}

	private static WardItem ward(String key, String label, ToIntFunction<OverviewSet> value,
			List<OverviewSet> own, Map<Long, List<OverviewSet>> comparable) {
		List<Double> perPlayer = comparable.values().stream().map(rows -> perSet(rows, value))
				.filter(Objects::nonNull).toList();
		return new WardItem(key, label, Decimals.stat(perSet(own, value)), Decimals.stat(PlayerMetric.mean(perPlayer)));
	}

	private static Double perSet(List<OverviewSet> rows, ToIntFunction<OverviewSet> value) {
		return rows.isEmpty() ? null : rows.stream().mapToInt(value).average().orElseThrow();
	}
}
