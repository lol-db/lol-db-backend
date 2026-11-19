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
	 * @param recordSets  세트 기록이 있는 세트 수 (플레이오프 포함). 아래 값은 모두 이 세트들로 계산한다
	 * @param avgRating   세트 평점(0~10) 평균, 소수 1자리. 평점이 계산된 세트가 없으면 null
	 * @param kda         (킬 + 어시스트) / 데스, 소수 2자리. 데스가 0이면 킬 + 어시스트. 세트가 없으면 null
	 * @param mainChampion 가장 많이 쓴 챔피언 (같으면 이름순). 세트가 없으면 null
	 */
	public record RosterPlayer(
			Long playerId,
			String nickname,
			String name,
			Position position,
			Integer seasonSets,
			Integer totalPoints,
			int recordSets,
			Double avgRating,
			int kills,
			int deaths,
			int assists,
			Double kda,
			MainChampion mainChampion) {
	}

	/** 대표 챔피언과 그 챔피언으로 뛴 세트 수 */
	public record MainChampion(Long championId, String name, int sets) {
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
	 * @param ranks             지표별 리그 내 순위
	 */
	public record TeamStats(
			int sets,
			Integer goldDiffAt15,
			Integer firstDragonRate,
			Integer firstTowerRate,
			Integer firstBaronRate,
			Integer avgPlaySeconds,
			Integer blueWinRate,
			Integer redWinRate,
			StatRanks ranks) {
	}

	/**
	 * 팀 지표별 리그 내 순위 (1위부터, 같은 값이면 같은 순위). 값이 없는 지표는 null이다.
	 * 평균 경기 시간은 짧을수록, 나머지는 클수록 높은 순위다.
	 *
	 * @param teams 세트 기록이 있는 팀 수 (예: 10팀 중 3위)
	 */
	public record StatRanks(
			int teams,
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
