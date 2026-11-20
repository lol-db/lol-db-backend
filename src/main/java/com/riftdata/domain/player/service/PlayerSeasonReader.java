package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PositionLeader;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 다른 도메인이 선수 시즌 지표(점수·등급)를 읽는 창구. 구현은 선수 도메인이 맡는다.
 * 등급 계산 규칙(백분위 컷)은 선수 도메인에서 정한다. 홈(API-03)에서 쓴다.
 */
public interface PlayerSeasonReader {

	/** 선수 ID별 시즌 종합 등급 (A+, A, ...). 등급이 없는 선수(10세트 미만)는 결과에서 빠진다. */
	Map<Long, String> findSeasonGrades(Collection<Long> playerIds);

	/** 포지션마다 종합 점수 1위 (등급 있는 선수만), 포지션 순(TOP → SUPPORT) */
	List<PositionLeader> findPositionLeaders();
}
