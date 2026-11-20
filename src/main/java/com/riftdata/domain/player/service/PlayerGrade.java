package com.riftdata.domain.player.service;

/** 화면설계서의 상위 백분율(100 - 종합 점수)과 등급 컷을 적용하는 도구입니다. */
public final class PlayerGrade {
	private PlayerGrade() {
	}

	/** 종합 점수는 적재 후 같은 포지션 기준으로 계산된 0~100 점수입니다. */
	public static String fromPoints(Integer totalPoints) {
		if (totalPoints == null || totalPoints < 0 || totalPoints > 100) {
			return null;
		}
		int topPercent = 100 - totalPoints;
		if (topPercent <= 5) return "A+";
		if (topPercent <= 15) return "A";
		if (topPercent <= 25) return "A-";
		if (topPercent <= 40) return "B+";
		if (topPercent <= 55) return "B";
		if (topPercent <= 70) return "B-";
		if (topPercent <= 85) return "C+";
		return "C";
	}
}
