package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerGamesResponse;
import com.riftdata.domain.player.dto.PlayerGamesResponse.ChampionInfo;
import com.riftdata.domain.player.dto.PlayerGamesResponse.Opponent;
import com.riftdata.domain.player.dto.PlayerGamesResponse.SetInfo;
import com.riftdata.domain.player.dto.PlayerGamesResponse.StageInfo;
import com.riftdata.domain.player.dto.PlayerPageResponse;
import com.riftdata.domain.player.repository.PlayerGamesRepository;
import com.riftdata.domain.player.repository.PlayerGamesRepository.GameRow;
import com.riftdata.domain.player.repository.PlayerGamesRepository.MatchTeam;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 선수 출전 기록을 필터링한 다음 경기 단위로 페이지를 나눕니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerGamesService {
	private final PlayerRepository playerRepository;
	private final PlayerGamesRepository playerGamesRepository;

	public PlayerPageResponse<PlayerGamesResponse> getGames(Long playerId, String result,
			Long opponentTeamId, String patch, Long championId, int page, int size) {
		PlayerQuerySupport.validatePage(page, size);
		PlayerQuerySupport.validateChampionId(championId);
		String normalizedPatch = PlayerQuerySupport.normalizePatch(patch);
		if (opponentTeamId != null && opponentTeamId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		Boolean setWin = parseResult(result);
		PlayerQuerySupport.requirePlayer(playerRepository, playerId);
		List<GameRow> rows = playerGamesRepository.findGames(
				playerId, setWin, opponentTeamId, normalizedPatch, championId);
		Map<Long, List<GameRow>> games = rows.stream().collect(Collectors.groupingBy(
				GameRow::matchId, LinkedHashMap::new, Collectors.toList()));
		PlayerPageResponse<List<GameRow>> selected = PlayerPageResponse.from(
				List.copyOf(games.values()), page, size);
		List<Long> matchIds = selected.content().stream().map(rs -> rs.getFirst().matchId()).toList();
		Map<MatchTeam, Integer> wins = playerGamesRepository.findSeriesWins(matchIds);
		return new PlayerPageResponse<>(selected.content().stream().map(rs -> toGame(rs, wins)).toList(),
				page, size, selected.totalElements(), selected.totalPages());
	}

	private static Boolean parseResult(String result) {
		if (result == null) return null;
		return switch (result) {
			case "WIN" -> true;
			case "LOSS" -> false;
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
		};
	}

	private static PlayerGamesResponse toGame(List<GameRow> rows, Map<MatchTeam, Integer> wins) {
		GameRow first = rows.getFirst();
		// 현재 소속 팀 대신 해당 경기의 선수 세트에 저장된 팀을 기준으로 표시합니다.
		boolean playedForTeam1 = first.ownTeamId().equals(first.team1Id());
		Long opposingId = playedForTeam1 ? first.team2Id() : first.team1Id();
		String opposingCode = playedForTeam1 ? first.team2Code() : first.team1Code();
		int ownWins = wins.getOrDefault(new MatchTeam(first.matchId(), first.ownTeamId()), 0);
		int opposingWins = wins.getOrDefault(new MatchTeam(first.matchId(), opposingId), 0);
		Boolean seriesWin = ownWins == opposingWins ? null : ownWins > opposingWins;
		return new PlayerGamesResponse(first.matchId(), first.playedAt().toLocalDate(),
				new StageInfo(first.stageId(), first.stageName(), first.regular()),
				new Opponent(opposingId, opposingCode), ownWins + ":" + opposingWins, seriesWin,
				rows.stream().map(PlayerGamesService::toSet).toList());
	}

	private static SetInfo toSet(GameRow row) {
		Integer goldDiff15 = row.goldAt15() == null || row.opponentGoldAt15() == null
				? null : row.goldAt15() - row.opponentGoldAt15();
		Double csPerMin = row.durationSec() == 0 ? null
				: PlayerQuerySupport.round(row.cs() * 60.0 / row.durationSec(), 2);
		Double dpm = row.durationSec() == 0 ? null
				: PlayerQuerySupport.round(row.damage() * 60.0 / row.durationSec(), 2);
		return new SetInfo(row.setNo(), new ChampionInfo(row.championId(), row.championName()), row.win(),
				row.kills(), row.deaths(), row.assists(), csPerMin, dpm, goldDiff15,
				row.durationSec(), Rating.fromPoints(row.totalPoints()), row.pog());
	}
}
