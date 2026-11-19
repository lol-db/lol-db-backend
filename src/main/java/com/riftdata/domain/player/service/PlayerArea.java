package com.riftdata.domain.player.service;

import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.global.common.Position;

/** 화면설계서의 여섯 영역과 포지션별 표시용 가중치입니다. 저장된 점수를 다시 계산하지 않습니다. */
public enum PlayerArea {
	COMBAT("전투", new int[]{20, 30, 25, 20, 30}),
	DAMAGE("딜량", new int[]{20, 10, 25, 30, 5}),
	LANE("라인전", new int[]{25, 15, 20, 15, 10}),
	GROWTH("성장", new int[]{20, 15, 15, 20, 5}),
	VISION("시야", new int[]{5, 20, 5, 5, 40}),
	SURVIVAL("생존", new int[]{10, 10, 10, 10, 10});

	private final String label;
	private final int[] weights;

	PlayerArea(String label, int[] weights) {
		this.label = label;
		this.weights = weights;
	}

	public String label() {
		return label;
	}

	public int weight(Position position) {
		return weights[position.ordinal()];
	}

	public Integer points(PlayerSeason season) {
		if (season == null) return null;
		Integer points = switch (this) {
			case COMBAT -> season.getCombatPoints();
			case DAMAGE -> season.getDamagePoints();
			case LANE -> season.getLaningPoints();
			case GROWTH -> season.getGrowthPoints();
			case VISION -> season.getWardPoints();
			case SURVIVAL -> season.getSurvivalPoints();
		};
		return points == null || points < 0 || points > 100 ? null : points;
	}
}
