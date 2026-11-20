package com.riftdata.domain.match.dto.response;

import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import java.util.List;

/**
 * 세트 상세 (API-07, SCR-004): 밴픽, 팀 오브젝트, 스코어보드, 골드 차.
 * teams는 블루 먼저다.
 */
public record SetDetailResponse(
		Long matchId,
		Integer setNo,
		String sourceGameId,
		String patch,
		Integer durationSec,
		Long winnerTeamId,
		List<TeamDetail> teams,
		List<GoldDiff> goldDiff) {

	/** 세트에서 한 팀. players는 포지션 순(TOP → SUPPORT)이다. */
	public record TeamDetail(
			Long teamId,
			String code,
			Side side,
			boolean win,
			List<BanResponse> bans,
			List<PickResponse> picks,
			Objectives objectives,
			List<PlayerLine> players) {
	}

	/** 밴 하나. order는 밴 순서 1~5다. */
	public record BanResponse(Integer order, Long championId, String name) {
	}

	/** 픽 하나. DB에 픽 순서가 없어 포지션 순으로 주고, 순서 필드는 없다. */
	public record PickResponse(Long championId, String name, Position position) {
	}

	/**
	 * 팀 오브젝트. kills·gold는 선수 기록의 합이다.
	 * 명세서의 heralds(전령)는 DB에 없어서 빼고, 저장된 voidGrubs(공허 유충)·inhibitors(억제기)를 준다.
	 */
	public record Objectives(
			int kills, int gold, Integer towers, Integer dragons, Integer voidGrubs, Integer barons,
			Integer inhibitors) {
	}

	/**
	 * 스코어보드 한 줄. cs는 미니언 + 몬스터 처치 수, dpm은 분당 챔피언 피해량,
	 * damageShare는 팀 피해량 합에서 차지하는 비율(0~1)이다. rating은 평점 계산 전이면 null이다.
	 */
	public record PlayerLine(
			Long playerId,
			String name,
			Position position,
			ChampionResponse champion,
			Integer kills,
			Integer deaths,
			Integer assists,
			Integer cs,
			Integer gold,
			Double dpm,
			Double damageShare,
			Integer visionScore,
			Double rating,
			boolean pog) {
	}

	/** minute분 시점의 골드 차 (블루 − 레드) */
	public record GoldDiff(int minute, int diff) {
	}
}
