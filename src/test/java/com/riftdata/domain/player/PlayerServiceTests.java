package com.riftdata.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riftdata.domain.player.dto.PlayerProfile;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.service.PlayerService;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.global.common.Position;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 MySQL을 조회하며, 테스트가 만든 선수와 팀은 종료 시 롤백됩니다. */
@SpringBootTest
@Transactional
class PlayerServiceTests {

	@Autowired PlayerService playerService;
	@Autowired PlayerRepository playerRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired EntityManager entityManager;

	@Test
	@DisplayName("선수 번호로 DB를 조회해 닉네임과 소속 팀의 기본 정보를 반환합니다.")
	void readsPlayerProfileFromDatabase() {
		Team team = teamRepository.save(Team.builder().code("PQTS").build());
		Player player = playerRepository.save(Player.builder()
				.team(team).nickname("QueryTestPlayer").name("테스트실명")
				.position(Position.JUNGLE).build());
		entityManager.flush();
		Long playerId = player.getId();
		Long teamId = team.getId();
		entityManager.clear(); // 메모리에 있던 객체 대신 DB에서 다시 읽도록 합니다.

		PlayerProfile profile = playerService.getProfile(playerId);

		assertThat(profile.playerId()).isEqualTo(playerId);
		assertThat(profile.name()).isEqualTo("QueryTestPlayer");
		assertThat(profile.position()).isEqualTo("JUNGLE");
		assertThat(profile.team().teamId()).isEqualTo(teamId);
		assertThat(profile.team().code()).isEqualTo("PQTS");
	}

	@Test
	@DisplayName("없는 선수 번호는 기존 PLAYER_NOT_FOUND 오류로 처리합니다.")
	void missingPlayerUsesExistingError() {
		assertThatThrownBy(() -> playerService.getProfile(Long.MAX_VALUE))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PLAYER_NOT_FOUND));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {0, -1})
	@DisplayName("비어 있거나 양수가 아닌 선수 번호는 입력 오류로 처리합니다.")
	void invalidPlayerIdUsesInputError(Long playerId) {
		assertThatThrownBy(() -> playerService.getProfile(playerId))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
	}

	@ParameterizedTest
	@CsvSource({"TOP,TOP", "JUNGLE,JUNGLE", "MID,MID", "ADC,ADC", "SUPPORT,SUPPORT"})
	@DisplayName("최신 경기 API와 같은 포지션 코드와 게임 닉네임을 사용합니다.")
	void mapsPositionsToApiNames(Position position, String expected) {
		Player player = Player.builder().nickname("QueryTestPlayer")
				.team(Team.builder().code("PQTS").build()).position(position).build();

		PlayerProfile profile = PlayerProfile.from(player);

		assertThat(profile.position()).isEqualTo(expected);
		assertThat(profile.name()).isEqualTo("QueryTestPlayer");
	}
}
