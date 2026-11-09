package com.riftdata.global.common;

/**
 * 포지션. DB에는 ENUM('TOP','JUNGLE','MID','ADC','SUPPORT')로 이름 그대로 저장한다.
 * CSV position 값(top, jng, mid, bot, sup)은 적재할 때 {@link #fromCsv(String)}로 바꾼다.
 */
public enum Position {
	TOP, JUNGLE, MID, ADC, SUPPORT;

	public static Position fromCsv(String value) {
		return switch (value.trim().toLowerCase()) {
			case "top" -> TOP;
			case "jng", "jungle" -> JUNGLE;
			case "mid" -> MID;
			case "bot", "adc" -> ADC;
			case "sup", "support" -> SUPPORT;
			default -> throw new IllegalArgumentException("알 수 없는 포지션: " + value);
		};
	}
}
