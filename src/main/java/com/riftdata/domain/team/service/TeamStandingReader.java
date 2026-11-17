package com.riftdata.domain.team.service;

import com.riftdata.domain.team.dto.TeamStanding;
import java.util.Collection;
import java.util.Map;

/**
 * 다른 도메인이 팀의 현재 순위를 읽는 창구. 구현은 팀·순위 도메인(API-08 순위 계산)이 맡는다.
 * 경기 상세(API-06)가 양 팀의 순위·승·패를 보여 줄 때 쓴다.
 */
public interface TeamStandingReader {

	/** 팀 ID별 현재 순위. 순위표에 없는 팀은 결과에서 빠진다. */
	Map<Long, TeamStanding> findByTeamIds(Collection<Long> teamIds);
}
