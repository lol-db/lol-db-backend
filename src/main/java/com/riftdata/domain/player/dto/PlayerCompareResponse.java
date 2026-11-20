package com.riftdata.domain.player.dto;

import com.riftdata.domain.player.service.PlayerArea;
import com.riftdata.global.common.Position;
import java.time.LocalDate;
import java.util.List;

/**
 * 선수 비교 (API-18, SCR-015). GET /api/v1/players/compare?p1=&p2=
 * 배열은 모두 [p1, p2] 순서다.
 *
 * @param samePosition 두 선수의 주포지션이 같은지. 다르면 등급은 모두 null (등급은 같은 포지션 안의 백분위라 비교할 수 없음)
 */
public record PlayerCompareResponse(boolean samePosition, List<ComparedPlayer> players,
		List<ComparedMetric> metrics, HeadToHead headToHead) {

	/**
	 * @param sets         2026 시즌 실제 출전 세트 수
	 * @param winRate      세트 승률 0~1. 출전이 없으면 null
	 * @param overallScore 저장된 시즌 종합 점수. 10세트 미만이거나 미적재면 null
	 * @param grade        종합 등급. samePosition이 false면 null
	 */
	public record ComparedPlayer(Long playerId, String name, String teamCode, Position position, int sets,
			Double winRate, Double overallScore, String grade, List<AreaScore> areas) {
	}

	/** @param grade samePosition이 false면 null */
	public record AreaScore(PlayerArea area, Double score, String grade) {
	}

	/**
	 * @param values    [p1 값, p2 값]. 계산할 수 없으면 null
	 * @param direction 1이면 높을수록 좋음, -1이면 낮을수록 좋음
	 * @param better    더 좋은 쪽 인덱스 (0 또는 1). 같거나 한쪽이 null이면 null
	 */
	public record ComparedMetric(String code, String label, List<Double> values, int direction, Integer better) {
	}

	/**
	 * 같은 세트에 서로 다른 팀으로 나온 기록.
	 *
	 * @param wins [p1이 이긴 세트 수, p2가 이긴 세트 수]
	 * @param sets 시간 순
	 */
	public record HeadToHead(List<Integer> wins, List<HeadToHeadSet> sets) {
	}

	/**
	 * @param champions  [p1 챔피언 이름, p2 챔피언 이름]
	 * @param ratings    [p1 평점, p2 평점]. 계산 전이면 null
	 * @param goldDiff15     p1 − p2의 15분 골드. 두 선수가 그 세트에서 같은 포지션(라인 상대)이 아니거나 기록이 없으면 null
	 * @param winnerPlayerId 그 세트를 이긴 선수 ID
	 */
	public record HeadToHeadSet(Long matchId, int setNo, LocalDate date, List<String> champions, List<Double> ratings,
			Double goldDiff15, Long winnerPlayerId) {
	}
}
