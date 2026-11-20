package com.riftdata.domain.record.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.riftdata.domain.record.dto.SetPog;
import com.riftdata.domain.record.dto.SetWinner;
import com.riftdata.support.MatchFixture;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 경기 여러 개의 세트 승자·POG를 한 번에 읽는 쿼리 (실제 MySQL, 끝나면 롤백) */
@SpringBootTest
@Transactional
class SetResultQueryTests {

	@Autowired EntityManager em;
	@Autowired TeamSetRepository teamSetRepository;
	@Autowired PlayerSetRepository playerSetRepository;

	MatchFixture.Data data;

	@BeforeEach
	void setUp() {
		data = MatchFixture.create(em);
	}

	@Test
	void findsSetWinnersInSetOrder() {
		List<SetWinner> winners = teamSetRepository.findSetWinners(
				List.of(data.finished().getId(), data.scheduled().getId()));

		Long a = data.teamA().getId();
		Long b = data.teamB().getId();
		Long matchId = data.finished().getId();
		// 예정 경기는 세트가 없어서 빠진다
		assertThat(winners)
				.extracting(SetWinner::matchId, SetWinner::setNumber, SetWinner::winnerTeamId)
				.containsExactly(tuple(matchId, 1, a), tuple(matchId, 2, b), tuple(matchId, 3, a));
	}

	@Test
	void findsSetPogsWithPlayerAndChampion() {
		List<SetPog> pogs = playerSetRepository.findSetPogs(List.of(data.finished().getId()));

		assertThat(pogs)
				.extracting(SetPog::setNumber, SetPog::nickname, SetPog::championName, SetPog::totalPoints)
				.containsExactly(
						tuple(1, "ZZA_MID", "ZZChamp2", 88),
						tuple(2, "ZZB_MID", "ZZChamp7", 80),
						tuple(3, "ZZA_MID", "ZZChamp2", 95));
		assertThat(pogs.getFirst().teamId()).isEqualTo(data.teamA().getId());
		assertThat(pogs.getFirst().rating()).isEqualTo(9.0);
	}

	@Test
	void returnsEmptyForMatchWithoutSets() {
		List<Long> scheduledOnly = List.of(data.scheduled().getId());

		assertThat(teamSetRepository.findSetWinners(scheduledOnly)).isEmpty();
		assertThat(playerSetRepository.findSetPogs(scheduledOnly)).isEmpty();
	}
}
