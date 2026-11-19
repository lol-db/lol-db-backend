package com.riftdata.domain.team.repository;

import com.riftdata.global.common.Side;
import java.time.LocalTime;

/** 팀 지표 계산에 쓰는 한 팀의 세트 기록 한 줄. */
public record TeamSetSummary(
		Long setId,
		Side side,
		Boolean victory,
		Boolean firstDragon,
		Boolean firstTower,
		Boolean firstBaron,
		LocalTime playTime) {
}
