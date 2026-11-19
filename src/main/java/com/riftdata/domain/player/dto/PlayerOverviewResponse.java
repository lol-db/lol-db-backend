package com.riftdata.domain.player.dto;

import com.riftdata.global.common.Position;
import java.time.LocalDate;
import java.util.List;

/** 선수 종합 탭에 필요한 점수, 주요 지표와 경기 기록입니다. */
public record PlayerOverviewResponse(
		CompareGroup compareGroup, Overall overall, List<AreaScore> areas,
		List<MostChampion> mostChampions, List<KeyMetric> keyMetrics,
		List<RecentSet> recentSets, List<RatingPoint> ratingTrend) {
	public record CompareGroup(Position position, int playerCount, int minSets) {
	}
	public record Overall(Double score, String grade, Double topPercent, Integer positionRank) {
	}
	public record AreaScore(String area, String label, Double score, String grade,
			Double topPercent, int weight) {
	}
	public record MostChampion(Long championId, String name, int sets, Double winRate,
			Double kda, Double dpm, Double avgRating) {
	}
	public record KeyMetric(String code, String label, Double value, Double positionAvg,
			Double topPercent, String grade, int direction) {
	}
	public record TeamInfo(Long teamId, String code) {
	}
	public record ChampionInfo(Long championId, String name) {
	}
	public record RecentSet(Long matchId, int setNo, LocalDate date, TeamInfo opponent,
			ChampionInfo champion, int kills, int deaths, int assists, boolean win, Double rating) {
	}
	public record RatingPoint(Long matchId, int setNo, LocalDate date, Double rating, boolean pog) {
	}
}
