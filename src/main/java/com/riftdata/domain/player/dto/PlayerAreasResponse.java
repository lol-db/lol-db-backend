package com.riftdata.domain.player.dto;

import com.riftdata.domain.player.service.PlayerArea;
import java.util.List;

/**
 * 선수 영역 탭 (API-16, SCR-009~011). GET /api/v1/players/{playerId}/areas?group=
 *
 * @param group     combat(전투·딜량), lane(라인전·성장), vision(생존·시야)
 * @param areaCards 그룹의 영역 2개
 * @param setSeries 세트별 추이, 시간 순. 그룹마다 필드가 다르다 (CombatPoint / LanePoint / VisionPoint)
 * @param extra     그룹별 추가 정보 (CombatExtra / LaneExtra / VisionExtra)
 */
public record PlayerAreasResponse(String group, List<AreaCard> areaCards, List<SetPoint> setSeries, Extra extra) {

	/**
	 * 영역 카드.
	 *
	 * @param score      저장된 시즌 영역 점수 0~100. 필터가 있거나 10세트 미만·점수 미적재면 null
	 * @param topPercent 100 − score
	 * @param metrics    영역에 속한 지표 (GET /metrics의 영역 분류)
	 */
	public record AreaCard(PlayerArea area, String label, Double score, String grade, Double topPercent,
			List<AreaMetric> metrics) {
	}

	/**
	 * 영역 지표 하나. 종합 탭의 keyMetrics와 같은 계산이다.
	 *
	 * @param positionAvg 같은 포지션 비교 집단(시즌 10세트 이상)의 평균. 같은 필터 적용
	 * @param topPercent  상위 백분율 0~100. 필터가 있거나 10세트 미만이면 null
	 */
	public record AreaMetric(String code, String label, Double value, Double positionAvg, Double topPercent,
			String grade, int direction) {
	}

	/** 세트별 추이 한 점 */
	public sealed interface SetPoint permits CombatPoint, LanePoint, VisionPoint {
	}

	/** combat: 분당 피해량(경기 시간 0초면 null), 킬, 어시스트 */
	public record CombatPoint(Long matchId, int setNo, boolean win, Double dpm, int kills, int assists)
			implements SetPoint {
	}

	/** lane: 15분 골드 차(상대 기록이 없으면 null), 분당 CS */
	public record LanePoint(Long matchId, int setNo, boolean win, Double goldDiff15, Double csPerMin)
			implements SetPoint {
	}

	/** vision: 데스, 분당 시야 점수 */
	public record VisionPoint(Long matchId, int setNo, boolean win, int deaths, Double visionScorePerMin)
			implements SetPoint {
	}

	/** 그룹별 추가 정보 */
	public sealed interface Extra permits CombatExtra, LaneExtra, VisionExtra {
	}

	/** combat: 킬 관여 구성(도넛 차트)과 챔피언별 DPM */
	public record CombatExtra(KillParticipation killParticipation, List<ChampionDamage> championDamage)
			implements Extra {
	}

	/**
	 * 킬 관여 구성. 세트 합계.
	 *
	 * @param notInvolved 팀 킬 − 킬 − 어시스트 (관여하지 않은 팀 킬)
	 * @param teamKills   당시 팀 전체 킬
	 */
	public record KillParticipation(int kills, int assists, long notInvolved, long teamKills) {
	}

	/** 챔피언별 분당 피해량, DPM 내림차순 */
	public record ChampionDamage(Long championId, String name, int sets, Double dpm) {
	}

	/** lane: 같은 포지션 상대별 15분 지표 */
	public record LaneExtra(List<LaneMatchup> laneMatchups) implements Extra {
	}

	/**
	 * 라인 상대 한 명. 세트 수 내림차순, 같으면 닉네임 순.
	 *
	 * @param teamCode   가장 최근 맞대결 때 상대 팀
	 * @param goldDiff15 상대 대비 15분 골드 차 평균 (csDiff15, xpDiff15도 같음). 값이 있는 세트가 없으면 null
	 * @param avgRating  이 상대와의 세트에서 대상 선수의 평균 평점
	 */
	public record LaneMatchup(Long opponentPlayerId, String name, String teamCode, int sets, int wins, int losses,
			Double goldDiff15, Double csDiff15, Double xpDiff15, Double avgRating) {
	}

	/** vision: 와드 요약 */
	public record VisionExtra(List<WardItem> wardSummary) implements Extra {
	}

	/**
	 * 와드 요약 한 줄. 세트당 평균.
	 *
	 * @param key         WARDS_PLACED, WARDS_REMOVED, CONTROL_WARDS_BOUGHT, VISION_SCORE
	 * @param positionAvg 같은 포지션 비교 집단 선수별 세트당 평균의 평균
	 */
	public record WardItem(String key, String label, Double value, Double positionAvg) {
	}
}
