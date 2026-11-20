package com.riftdata.domain.player.dto;

import java.time.LocalDate;
import java.util.List;

/** 선수의 출전 세트를 경기 단위로 묶은 API-15 응답입니다. */
public record PlayerGamesResponse(
		Long matchId, LocalDate date, StageInfo stage, Opponent opponent,
		String score, Boolean win, List<SetInfo> sets) {

	public record StageInfo(Long stageId, String name, boolean regular) {
	}

	public record Opponent(Long teamId, String code) {
	}

	public record ChampionInfo(Long championId, String name) {
	}

	public record SetInfo(
			int setNo, ChampionInfo champion, boolean win,
			int kills, int deaths, int assists, Double csPerMin, Double dpm,
			Integer goldDiff15, int durationSec, Double rating, boolean pog) {
	}
}
