package com.riftdata.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.riftdata.domain.player.dto.PositionLeader;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.service.PlayerSeasonReader;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.global.common.Position;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 홈에서 사용하는 실제 선수 조회 빈과 MySQL을 검증하며, 각 검사 후 데이터는 롤백됩니다. */
@SpringBootTest
@Transactional
class PlayerSeasonServiceTests {

	@Autowired PlayerSeasonReader playerSeasonReader;
	@Autowired EntityManager em;

	private Team team;

	@BeforeEach
	void setUp() {
		team = persist(Team.builder().code("PSS").build());
	}

	@Test
	@DisplayName("요청한 선수 중 시즌 점수와 출전 조건이 있는 선수의 등급만 반환합니다.")
	void returnsOnlyRequestedEligibleGrades() {
		Player first = player("PS_First", Position.TOP);
		Player second = player("PS_Second", Position.MID);
		Player unrequested = player("PS_Unrequested", Position.ADC);
		Player tooFew = player("PS_TooFew", Position.JUNGLE);
		Player unscored = player("PS_Unscored", Position.SUPPORT);
		Player noSeason = player("PS_NoSeason", Position.TOP);
		season(first, 10, 95);
		season(second, 20, 85);
		season(unrequested, 20, 100);
		season(tooFew, 9, 100);
		season(unscored, 20, null);
		flushAndClear();

		Map<Long, String> grades = playerSeasonReader.findSeasonGrades(List.of(
				first.getId(), second.getId(), tooFew.getId(), unscored.getId(),
				noSeason.getId(), Long.MAX_VALUE));

		assertThat(grades).containsOnly(entry(first.getId(), "A+"), entry(second.getId(), "A"));
	}

	@Test
	@DisplayName("요청한 선수 번호가 없으면 다른 선수의 등급을 반환하지 않습니다.")
	void emptyRequestDoesNotReturnEveryGrade() {
		season(player("PS_Existing", Position.MID), 10, 95);
		flushAndClear();

		assertThat(playerSeasonReader.findSeasonGrades(List.of())).isEmpty();
	}

	@Test
	@DisplayName("중복 선수 번호는 한 번만 반환하며 존재하지 않는 번호는 제외합니다.")
	void duplicateAndUnknownIdsAreHandledTogether() {
		Player player = player("PS_Duplicate", Position.MID);
		season(player, 10, 75);
		flushAndClear();

		assertThat(playerSeasonReader.findSeasonGrades(List.of(
				player.getId(), Long.MAX_VALUE, player.getId())))
				.containsOnly(entry(player.getId(), "A-"));
	}

	@ParameterizedTest
	@CsvSource({"0,false", "9,false", "10,true", "11,true"})
	@DisplayName("시즌 출전 수가 정확히 10세트부터 등급과 포지션 리더의 대상입니다.")
	void enforcesTenSetBoundary(int sets, boolean eligible) {
		Player player = player("PS_Boundary", Position.TOP);
		season(player, sets, 95);
		flushAndClear();

		Map<Long, String> grades = playerSeasonReader.findSeasonGrades(List.of(player.getId()));
		List<PositionLeader> leaders = playerSeasonReader.findPositionLeaders();

		if (eligible) {
			assertThat(grades).containsOnly(entry(player.getId(), "A+"));
			assertThat(leaders).extracting(PositionLeader::playerId).containsExactly(player.getId());
		} else {
			assertThat(grades).isEmpty();
			assertThat(leaders).isEmpty();
		}
	}

	@ParameterizedTest
	@CsvSource({"0,C", "100,A+"})
	@DisplayName("유효한 점수의 양 끝인 0점과 100점도 등급과 리더 정보에 반영합니다.")
	void acceptsBothEndsOfValidScoreRange(int points, String grade) {
		Player player = player("PS_ScoreEdge", Position.ADC);
		season(player, 10, points);
		flushAndClear();

		assertThat(playerSeasonReader.findSeasonGrades(List.of(player.getId())))
				.containsOnly(entry(player.getId(), grade));
		assertThat(playerSeasonReader.findPositionLeaders()).containsExactly(
				new PositionLeader(Position.ADC, player.getId(), "PS_ScoreEdge", "PSS",
						(double) points, grade));
	}

	@Test
	@DisplayName("포지션 리더는 등급 대상 중 가장 높은 점수의 선수입니다.")
	void selectsHighestEligibleScore() {
		Player lower = player("PS_Lower", Position.JUNGLE);
		Player higher = player("PS_Higher", Position.JUNGLE);
		season(lower, 50, 75);
		season(higher, 10, 95);
		season(player("PS_UnderTen", Position.JUNGLE), 9, 100);
		season(player("PS_NoPoints", Position.JUNGLE), 50, null);
		player("PS_NoCache", Position.JUNGLE);
		flushAndClear();

		assertThat(playerSeasonReader.findPositionLeaders()).extracting(PositionLeader::playerId)
				.containsExactly(higher.getId());
	}

	@Test
	@DisplayName("종합 점수가 같으면 세트 수나 시즌 저장 순서와 관계없이 작은 선수 번호를 선택합니다.")
	void breaksScoreTieByPlayerId() {
		Player smallerId = player("PS_TieFirst", Position.MID);
		Player largerId = player("PS_TieSecond", Position.MID);
		// 시즌 행은 선수 번호와 반대 순서로 저장하여 저장 순서에 의존하지 않는지 확인합니다.
		season(largerId, 50, 85);
		season(smallerId, 10, 85);
		flushAndClear();

		assertThat(playerSeasonReader.findPositionLeaders()).extracting(PositionLeader::playerId)
				.containsExactly(smallerId.getId());
	}

	@Test
	@DisplayName("리더 정보에는 실명 대신 닉네임과 현재 소속 팀을 반환합니다.")
	void mapsNicknameCurrentTeamAndSeasonScore() {
		Player player = player("PS_Transferred", Position.SUPPORT);
		season(player, 12, 60);
		Team currentTeam = persist(Team.builder().code("PSC").build());
		player.changeTeam(currentTeam);
		flushAndClear();

		assertThat(playerSeasonReader.findPositionLeaders()).containsExactly(
				new PositionLeader(Position.SUPPORT, player.getId(), "PS_Transferred", "PSC", 60.0, "B+"));
	}

	@Test
	@DisplayName("포지션 리더는 점수나 저장 순서와 관계없이 TOP부터 SUPPORT 순서입니다.")
	void ordersAllPositionsInGameOrder() {
		season(player("PS_Support", Position.SUPPORT), 10, 100);
		season(player("PS_Adc", Position.ADC), 10, 95);
		season(player("PS_Mid", Position.MID), 10, 85);
		season(player("PS_Jungle", Position.JUNGLE), 10, 75);
		season(player("PS_Top", Position.TOP), 10, 60);
		flushAndClear();

		assertThat(playerSeasonReader.findPositionLeaders()).extracting(PositionLeader::position)
				.containsExactly(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
	}

	@Test
	@DisplayName("리더가 없는 포지션은 빈 선수 정보 대신 결과에서 제외합니다.")
	void omitsPositionsWithoutAnEligiblePlayer() {
		season(player("PS_OnlySupport", Position.SUPPORT), 10, 85);
		season(player("PS_OnlyTop", Position.TOP), 10, 95);
		season(player("PS_ShortMid", Position.MID), 9, 100);
		season(player("PS_NullJungle", Position.JUNGLE), 20, null);
		player("PS_EmptyAdc", Position.ADC);
		flushAndClear();

		assertThat(playerSeasonReader.findPositionLeaders()).extracting(PositionLeader::position)
				.containsExactly(Position.TOP, Position.SUPPORT);
	}

	@Test
	@DisplayName("시즌 데이터가 없으면 등급과 포지션 리더는 빈 결과입니다.")
	void returnsEmptyResultsWhenSeasonDataIsAbsent() {
		Player player = player("PS_Absent", Position.TOP);
		flushAndClear();

		assertThat(playerSeasonReader.findSeasonGrades(List.of(player.getId()))).isEmpty();
		assertThat(playerSeasonReader.findPositionLeaders()).isEmpty();
	}

	private Player player(String nickname, Position position) {
		return persist(Player.builder().team(team).nickname(nickname).name("선수실명")
				.position(position).build());
	}

	private void season(Player player, int sets, Integer points) {
		persist(PlayerSeason.builder().player(player).sets(sets).totalPoints(points)
				.calculatedAt(LocalDateTime.of(2026, 11, 19, 12, 0)).build());
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}
}
