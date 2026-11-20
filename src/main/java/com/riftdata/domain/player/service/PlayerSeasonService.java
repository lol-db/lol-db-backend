package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PositionLeader;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository.SeasonPlayer;
import com.riftdata.global.common.Position;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 API가 사용하는 시즌 등급과 포지션별 1위 선수 정보를 제공합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerSeasonService implements PlayerSeasonReader {

	private final PlayerSeasonRepository playerSeasonRepository;

	/** 요청한 선수 중 10세트 이상이며 유효한 시즌 점수가 있는 선수의 등급만 반환합니다. */
	@Override
	public Map<Long, String> findSeasonGrades(Collection<Long> playerIds) {
		if (playerIds.isEmpty()) {
			return Map.of();
		}
		return playerSeasonRepository.findGradeCandidatesByPlayerIds(playerIds).stream()
				.collect(Collectors.toMap(SeasonPlayer::getPlayerId,
						season -> PlayerGrade.fromPoints(season.getTotalPoints())));
	}

	/** 포지션마다 종합 점수가 가장 높은 선수를 선택하고 TOP부터 SUPPORT 순서로 반환합니다. */
	@Override
	public List<PositionLeader> findPositionLeaders() {
		Map<Position, PositionLeader> leaders = new EnumMap<>(Position.class);
		// 점수가 같으면 선수 ID가 작은 선수를 선택해 조회할 때마다 결과가 바뀌지 않도록 합니다.
		Comparator<SeasonPlayer> order = Comparator
				.comparing(SeasonPlayer::getTotalPoints, Comparator.reverseOrder())
				.thenComparing(SeasonPlayer::getPlayerId);
		playerSeasonRepository.findGradeCandidates().stream().sorted(order).forEach(season ->
				leaders.putIfAbsent(season.getPosition(), new PositionLeader(
						season.getPosition(), season.getPlayerId(), season.getNickname(),
						season.getTeamCode(), season.getTotalPoints().doubleValue(),
						PlayerGrade.fromPoints(season.getTotalPoints()))));
		return List.copyOf(leaders.values());
	}
}
