package com.riftdata.global.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 한국 시간 변환. DB의 DATETIME(경기일시 등)은 한국 시간으로 저장되어 있고,
 * API 응답은 "2026-04-26T17:00:00+09:00"처럼 오프셋을 붙여서 보낸다.
 */
public final class KstTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private KstTime() {
	}

	/** DB의 LocalDateTime을 +09:00 오프셋이 붙은 응답용 시각으로. null이면 null */
	public static OffsetDateTime toOffset(LocalDateTime dateTime) {
		return dateTime == null ? null : dateTime.atZone(ZONE).toOffsetDateTime();
	}
}
