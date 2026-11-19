package com.riftdata.domain.team.dto;

import com.riftdata.global.common.Position;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 팀 상세 (SCR-006). GET /api/v1/teams/{teamId}
 *
 * @param standing 정규 시즌 순위와 성적 (순위표의 같은 팀 행)
 */
public record TeamDetailResponse(
		Long teamId,
		String code,
		StandingResponse standing,
		List<RosterPlayer> roster,
		TeamStats stats,
		List<RecentMatch> recentMatches) {

	/**
	 * 로스터 선수 한 명. 포지션 순(TOP → SUPPORT).
	 *
	 * @param name        실명. 입력 전이면 null
	 * @param seasonSets  시즌 출전 세트 수. 시즌 지표 계산 전이면 null
	 * @param totalPoints 시즌 종합 점수 0~100. 10세트 미만이거나 계산 전이면 null
	 */
	public record RosterPlayer(
			Long playerId,
			String nickname,
			String name,
			Position position,
			Integer seasonSets,
			Integer totalPoints) {
	}

	/**
	 * 팀 단위 지표. 세트 기록이 없으면 비율·평균은 null이다.
	 *
	 * @param sets              기록이 있는 세트 수 (플레이오프 포함)
	 * @param goldDiffAt15      세트당 평균 15분 골드 차 (양 팀 5명 모두 값이 있는 세트만)
	 * @param firstDragonRate   첫 용 획득률 0~100
	 * @param firstTowerRate    첫 포탑 획득률 0~100
	 * @param firstBaronRate    첫 바론 획득률 0~100
	 * @param avgPlaySeconds    평균 경기 시간(초). 화면에는 mm:ss로 표시
	 * @param blueWinRate       블루 진영 세트 승률 0~100. 블루 세트가 없으면 null
	 * @param redWinRate        레드 진영 세트 승률 0~100. 레드 세트가 없으면 null
	 */
	public record TeamStats(
			int sets,
			Integer goldDiffAt15,
			Integer firstDragonRate,
			Integer firstTowerRate,
			Integer firstBaronRate,
			Integer avgPlaySeconds,
			Integer blueWinRate,
			Integer redWinRate) {
	}

	/**
	 * 끝난 경기 하나 (최근 순, 최대 5개, 플레이오프 포함). 클릭 → 경기 상세 (SCR-003)
	 *
	 * @param setWins   이 팀이 딴 세트 수
	 * @param setLosses 상대가 딴 세트 수
	 */
	public record RecentMatch(
			Long matchId,
			LocalDateTime matchTime,
			String stageName,
			Long opponentTeamId,
			String opponentCode,
			int setWins,
			int setLosses,
			boolean win) {
	}
}
