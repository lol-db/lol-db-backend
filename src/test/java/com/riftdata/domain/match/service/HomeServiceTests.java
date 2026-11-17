package com.riftdata.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riftdata.domain.match.dto.response.HomeResponse;
import com.riftdata.domain.record.dto.RatedSet;
import com.riftdata.domain.record.dto.RecentPog;
import com.riftdata.global.common.Position;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 홈의 집계 규칙 (DB 없이) */
class HomeServiceTests {

	private static final LocalDateTime DAY1 = LocalDateTime.of(2026, 4, 20, 17, 0);
	private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 4, 22, 17, 0);

	@Test
	@DisplayName("평점 상위는 3세트 미만 선수를 빼고, 평균이 같으면 세트가 많은 선수가 앞이다")
	void topRatedRules() {
		List<RatedSet> sets = List.of(
				// 1번: 2세트뿐이라 평균 10.0이어도 제외
				rated(1L, 100L, DAY1, 100), rated(1L, 100L, DAY2, 100),
				// 2번: 3세트 평균 7.0
				rated(2L, 100L, DAY1, 75), rated(2L, 100L, DAY1, 50), rated(2L, 100L, DAY2, 62),
				// 3번: 4세트 평균 7.0 → 2번보다 앞
				rated(3L, 100L, DAY1, 75), rated(3L, 100L, DAY1, 50), rated(3L, 100L, DAY2, 75),
				rated(3L, 100L, DAY2, 50));

		List<HomeResponse.TopRatedPlayer> items = HomeService.topRatedPlayers(sets);

		assertThat(items).extracting(HomeResponse.TopRatedPlayer::playerId).containsExactly(3L, 2L);
		assertThat(items).extracting(HomeResponse.TopRatedPlayer::rank).containsExactly(1, 2);
		assertThat(items).extracting(HomeResponse.TopRatedPlayer::avgRating).containsExactly(7.0, 7.0);
		assertThat(items.getFirst().sets()).isEqualTo(4);
	}

	@Test
	@DisplayName("주 챔피언은 가장 많이 쓴 챔피언이고, 횟수가 같으면 더 최근에 쓴 챔피언이다")
	void mainChampionRules() {
		List<RatedSet> mostUsed = List.of(
				rated(1L, 100L, DAY1, 50), rated(1L, 100L, DAY1, 50), rated(1L, 200L, DAY2, 50));
		List<RatedSet> tie = List.of(
				rated(1L, 100L, DAY1, 50), rated(1L, 200L, DAY2, 50), rated(1L, 300L, DAY1, 50));

		assertThat(HomeService.topRatedPlayers(mostUsed).getFirst().mainChampion().championId()).isEqualTo(100L);
		assertThat(HomeService.topRatedPlayers(tie).getFirst().mainChampion().championId()).isEqualTo(200L);
	}

	@Test
	@DisplayName("최근 POG는 평점 순 3명이고, 평점이 같으면 최근 경기가 앞이다")
	void recentPogRules() {
		List<RecentPog> pogs = List.of(
				pog(1L, DAY1, 1, 80), pog(2L, DAY2, 1, 80), pog(3L, DAY1, 2, 95), pog(4L, DAY1, 3, 60));

		assertThat(HomeService.recentPogs(pogs)).extracting(HomeResponse.RecentPogResponse::playerId)
				.containsExactly(3L, 2L, 1L);
	}

	private static RatedSet rated(Long playerId, Long championId, LocalDateTime time, int points) {
		return new RatedSet(playerId, "P" + playerId, "T", Position.MID, championId, "C" + championId, points, time);
	}

	private static RecentPog pog(Long playerId, LocalDateTime time, int setNumber, int points) {
		return new RecentPog(time, 1L, setNumber, playerId, "P" + playerId, "T", 1L, "C", points);
	}
}
