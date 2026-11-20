package com.riftdata.domain.team.dto;

import com.riftdata.global.common.Position;
import java.time.LocalDate;
import java.util.List;

/**
 * 팀 상세 (API-11, SCR-006). GET /api/v1/teams/{teamId}
 * 팀명·감독 컬럼이 DB에 없어 팀은 teamId, code만 보낸다.
 *
 * @param rank  정규 시즌 순위 (API-08과 같은 계산)
 */
public record TeamDetailResponse(
		Long teamId,
		String code,
		int rank,
		int wins,
		int losses,
		int setWins,
		int setLosses,
		List<RosterPlayer> roster,
		List<TeamStat> teamStats,
		List<RecentMatch> recentMatches) {

	/**
	 * 로스터 선수 한 명. 포지션 순(TOP → SUP), 같은 포지션이면 닉네임 순.
	 *
	 * @param name         선수 닉네임 (화면 표시 이름)
	 * @param realName     실명. 입력 전이면 null
	 * @param grade        시즌 등급. 선수 도메인의 등급 기준이 정해지기 전까지 null
	 * @param sets         세트 기록이 있는 세트 수 (플레이오프 포함). 아래 값은 모두 이 세트들로 계산한다
	 * @param avgRating    세트 평점(0~10) 평균, 소수 1자리. 평점이 계산된 세트가 없으면 null
	 * @param kda          (K + A) ÷ max(D, 1), 소수 2자리. 세트가 없으면 null
	 * @param mainChampion 가장 많이 쓴 챔피언 (같으면 이름순). 세트가 없으면 null
	 */
	public record RosterPlayer(
			Long playerId,
			String name,
			String realName,
			Position position,
			String grade,
			int sets,
			Double avgRating,
			Double kda,
			MainChampion mainChampion) {
	}

	public record MainChampion(Long championId, String name) {
	}

	/**
	 * 팀 지표 하나.
	 *
	 * @param value      비율은 0~1(소수 3자리), 골드 차는 소수 2자리, 경기 시간은 초(정수). 세트 기록이 없으면 null
	 * @param leagueRank 리그 내 순위 (클수록 1위, 같은 값이면 같은 순위). 진영 승률·경기 시간은 순위를 매기지 않아 null
	 */
	public record TeamStat(String key, String label, Number value, Integer leagueRank) {
	}

	/**
	 * 끝난 경기 하나 (최근 순, 최대 5개, 플레이오프 포함). 클릭 → 경기 상세 (SCR-003)
	 *
	 * @param score 이 팀 세트 수 : 상대 세트 수 (예: "2:1")
	 */
	public record RecentMatch(Long matchId, LocalDate date, Opponent opponent, boolean win, String score) {
	}

	public record Opponent(Long teamId, String code) {
	}
}
