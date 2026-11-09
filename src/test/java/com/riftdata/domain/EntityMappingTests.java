package com.riftdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.riftdata.domain.champion.entity.Champion;
import com.riftdata.domain.champion.repository.ChampionRepository;
import com.riftdata.domain.match.entity.LckMatch;
import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.match.entity.Stage;
import com.riftdata.domain.match.repository.LckMatchRepository;
import com.riftdata.domain.match.repository.MatchSetRepository;
import com.riftdata.domain.match.repository.StageRepository;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.domain.record.entity.Ban;
import com.riftdata.domain.record.entity.PlayerSet;
import com.riftdata.domain.record.entity.TeamSet;
import com.riftdata.domain.record.repository.BanRepository;
import com.riftdata.domain.record.repository.PlayerSetRepository;
import com.riftdata.domain.record.repository.TeamSetRepository;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 엔티티 10개를 실제 MySQL에 저장하고 다시 읽어 본다.
 * 테스트가 끝나면 롤백되므로 DB에 데이터가 남지 않는다.
 */
@SpringBootTest
@Transactional
class EntityMappingTests {

	@Autowired EntityManager em;
	@Autowired TeamRepository teamRepository;
	@Autowired PlayerRepository playerRepository;
	@Autowired ChampionRepository championRepository;
	@Autowired StageRepository stageRepository;
	@Autowired LckMatchRepository matchRepository;
	@Autowired MatchSetRepository matchSetRepository;
	@Autowired TeamSetRepository teamSetRepository;
	@Autowired PlayerSetRepository playerSetRepository;
	@Autowired BanRepository banRepository;
	@Autowired PlayerSeasonRepository seasonRepository;

	@Test
	void savesAndReadsEveryEntity() {
		Team t1 = teamRepository.save(Team.builder().code("TST1").build());
		Team t2 = teamRepository.save(Team.builder().code("TST2").build());
		Player mid = playerRepository.save(Player.builder()
				.team(t1).nickname("TestMid").position(Position.MID).build());
		Player jungle = playerRepository.save(Player.builder()
				.team(t1).nickname("TestJungle").position(Position.JUNGLE).name("홍길동").build());
		Champion ahri = championRepository.save(Champion.builder().name("TestAhri").build());
		Champion azir = championRepository.save(Champion.builder().name("TestAzir").build());
		Stage stage = stageRepository.save(Stage.builder()
				.name("Test Round").stageOrder(99)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 2, 1)).regular(true).build());

		LckMatch match = matchRepository.save(LckMatch.builder()
				.matchTime(LocalDateTime.of(2026, 1, 14, 17, 0)).sets(3).team1(t1).team2(t2).stage(stage).build());
		MatchSet set1 = matchSetRepository.save(MatchSet.builder()
				.match(match).setNumber(1).gameId("test-game-1").version("26.01")
				.playTime(LocalTime.of(0, 30, 50)).build());

		TeamSet blue = teamSetRepository.save(TeamSet.builder()
				.matchSet(set1).team(t1)
				.side(Side.BLUE).victory(true).dragons(3).towers(9)
				.build());
		teamSetRepository.save(TeamSet.builder()
				.matchSet(set1).team(t2).side(Side.RED).victory(false).build());
		playerSetRepository.save(PlayerSet.builder()
				.teamSet(blue).position(Position.MID).player(mid).champion(ahri)
				.kills(5).deaths(1).assists(8).damageToChampions(28_500)
				.damageTakenPm(612).totalGolds(14_250).goldAt10(3_400).csAt10(95)
				.build());
		banRepository.save(Ban.builder().teamSet(blue).banOrder(1).champion(azir).build());
		seasonRepository.save(PlayerSeason.builder()
				.player(mid).sets(12).totalPoints(81)
				.calculatedAt(LocalDateTime.of(2026, 2, 2, 3, 0)).build());

		em.flush();
		em.clear();

		MatchSet savedSet = matchSetRepository.findByMatchIdAndSetNumber(match.getId(), 1).orElseThrow();
		assertThat(savedSet.getPlaySeconds()).isEqualTo(1850);
		assertThat(teamSetRepository.findByMatchSetId(savedSet.getId())).hasSize(2);

		TeamSet savedBlue = teamSetRepository.findByMatchSetIdAndTeamId(savedSet.getId(), t1.getId()).orElseThrow();
		assertThat(savedBlue.getSide()).isEqualTo(Side.BLUE);
		assertThat(savedBlue.getVictory()).isTrue();
		assertThat(savedBlue.getFirstBlood()).isFalse();
		assertThat(savedBlue.getTeam().getCode()).isEqualTo("TST1");

		PlayerSet savedMid = playerSetRepository.findByPlayerId(mid.getId()).getFirst();
		assertThat(savedMid.getPosition()).isEqualTo(Position.MID);
		assertThat(savedMid.getKills()).isEqualTo(5);
		assertThat(savedMid.getDamageTakenPm()).isEqualTo(612);
		assertThat(savedMid.getTotalGolds()).isEqualTo(14_250);
		assertThat(savedMid.getGoldAt15()).isNull();
		assertThat(savedMid.getTotalPoints()).isNull();
		assertThat(savedMid.getPog()).isFalse();
		assertThat(savedMid.getChampion().getName()).isEqualTo("TestAhri");
		assertThat(savedMid.getTeamSet().getMatchSet().getMatch().getStage().getName()).isEqualTo("Test Round");
		assertThat(playerSetRepository.findByTeamSetMatchSetId(savedSet.getId())).hasSize(1);

		assertThat(banRepository.findByTeamSetMatchSetId(savedSet.getId()))
				.extracting(ban -> ban.getChampion().getName()).containsExactly("TestAzir");

		Player savedJungle = playerRepository.findByNickname("TestJungle").orElseThrow();
		assertThat(savedJungle.getPosition()).isEqualTo(Position.JUNGLE);
		assertThat(savedJungle.getName()).isEqualTo("홍길동");
		assertThat(playerRepository.findById(mid.getId()).orElseThrow().getName()).isNull();

		assertThat(seasonRepository.findByPlayerId(mid.getId()).orElseThrow().getTotalPoints()).isEqualTo(81);
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getSets()).isEqualTo(3);
	}

	@Test
	void mapsCsvPositionValues() {
		assertThat(Position.fromCsv("jng")).isEqualTo(Position.JUNGLE);
		assertThat(Position.fromCsv("bot")).isEqualTo(Position.ADC);
		assertThat(Position.fromCsv("sup")).isEqualTo(Position.SUPPORT);
	}
}
