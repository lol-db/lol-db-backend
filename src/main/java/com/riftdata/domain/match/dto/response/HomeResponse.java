package com.riftdata.domain.match.dto.response;

import com.riftdata.domain.player.dto.PositionLeader;
import com.riftdata.global.common.Position;
import java.time.LocalDate;
import java.util.List;

/** 홈 화면 (API-03, SCR-001) 6개 영역을 한 번에 */
public record HomeResponse(
		LocalDate date,
		List<MatchSummaryResponse> todayMatches,
		TopRatedPlayers topRatedPlayers,
		List<StandingRow> standingsTop,
		List<PositionLeader> positionLeaders,
		List<RecentPogResponse> recentPogs) {

	/** 최근 평점 상위 선수. fromDate~toDate 경기에서 minSets 세트 이상 뛴 선수만 */
	public record TopRatedPlayers(LocalDate fromDate, LocalDate toDate, int minSets, List<TopRatedPlayer> items) {
	}

	/** mainChampion은 기간 안에서 가장 많이 쓴 챔피언, seasonGrade는 시즌 종합 등급(없으면 null)이다. */
	public record TopRatedPlayer(
			int rank,
			Long playerId,
			String name,
			String teamCode,
			Position position,
			int sets,
			ChampionResponse mainChampion,
			Double avgRating,
			String seasonGrade) {
	}

	/** 순위 미니표 한 줄. setDiff는 세트 득실이다. */
	public record StandingRow(
			Integer rank, Long teamId, String code, Integer wins, Integer losses, Integer setDiff) {
	}

	/** 최근 세트 POG 하나 */
	public record RecentPogResponse(
			LocalDate date,
			Long matchId,
			Integer setNo,
			Long playerId,
			String name,
			String teamCode,
			ChampionResponse champion,
			Double rating) {
	}
}
