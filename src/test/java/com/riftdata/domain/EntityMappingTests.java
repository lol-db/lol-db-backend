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
import com.riftdata.domain.player.entity.PlayerSeasonStat;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonStatRepository;
import com.riftdata.domain.record.entity.PlayerSetStat;
import com.riftdata.domain.record.entity.PlayerSetStatId;
import com.riftdata.domain.record.entity.SetBan;
import com.riftdata.domain.record.entity.TeamSetStat;
import com.riftdata.domain.record.entity.TeamSetStatId;
import com.riftdata.domain.record.repository.PlayerSetStatRepository;
import com.riftdata.domain.record.repository.SetBanRepository;
import com.riftdata.domain.record.repository.TeamSetStatRepository;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.domain.team.repository.TeamRepository;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
	@Autowired TeamSetStatRepository teamSetStatRepository;
	@Autowired PlayerSetStatRepository playerSetStatRepository;
	@Autowired SetBanRepository setBanRepository;
	@Autowired PlayerSeasonStatRepository seasonStatRepository;

	@Test
	void savesAndReadsEveryEntity() {
		Team t1 = teamRepository.save(Team.builder().teamCode("TST1").teamName("Test One").sourceTeamId("test-team-1").build());
		Team t2 = teamRepository.save(Team.builder().teamCode("TST2").teamName("Test Two").sourceTeamId("test-team-2").build());
		Player mid = playerRepository.save(Player.builder()
				.team(t1).playerName("TestMid").mainPosition(Position.MID).sourcePlayerId("test-player-1").build());
		Player jungle = playerRepository.save(Player.builder()
				.team(t1).playerName("TestJungle").mainPosition(Position.JGL).sourcePlayerId("test-player-2").build());
		Champion ahri = championRepository.save(Champion.builder().championName("TestAhri").build());
		Champion azir = championRepository.save(Champion.builder().championName("TestAzir").build());
		Stage stage = stageRepository.save(Stage.builder()
				.stageCode("TST").stageName("Test Round").stageOrder(99)
				.startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 2, 1)).regular(true).build());

		LckMatch match = matchRepository.save(LckMatch.builder()
				.stage(stage).matchAt(LocalDateTime.of(2026, 1, 14, 17, 0)).team1(t1).team2(t2).build());
		MatchSet set1 = matchSetRepository.save(MatchSet.builder()
				.match(match).setNo(1).sourceGameId("test-game-1").patch("26.01").durationSec(1850).build());

		TeamSetStat blue = teamSetStatRepository.save(TeamSetStat.builder()
				.id(TeamSetStatId.of(match.getId(), set1.getSetNo(), t1.getId()))
				.matchSet(set1).team(t1)
				.side(Side.BLUE).win(true).dragons(3).towers(9)
				.build());
		playerSetStatRepository.save(PlayerSetStat.builder()
				.id(PlayerSetStatId.of(blue.getId(), Position.MID))
				.teamSetStat(blue).player(mid).champion(ahri)
				.kills(5).deaths(1).assists(8).damageToChampions(28_500)
				.damageTakenPm(new BigDecimal("612.4")).goldAt10(3_400)
				.build());
		setBanRepository.save(SetBan.builder().teamSetStat(blue).banOrder(1).champion(azir).build());
		seasonStatRepository.save(PlayerSeasonStat.builder()
				.player(mid).setCount(12).overallScore(new BigDecimal("81.25"))
				.calculatedAt(LocalDateTime.of(2026, 2, 2, 3, 0)).build());

		em.flush();
		em.clear();

		assertThat(matchSetRepository.findByIdMatchIdOrderByIdSetNoAsc(match.getId())).hasSize(1);

		TeamSetStat savedBlue = teamSetStatRepository.findById(blue.getId()).orElseThrow();
		assertThat(savedBlue.getSide()).isEqualTo(Side.BLUE);
		assertThat(savedBlue.getWin()).isTrue();
		assertThat(savedBlue.getFirstBlood()).isFalse();
		assertThat(savedBlue.getTeam().getTeamCode()).isEqualTo("TST1");

		PlayerSetStat savedMid = playerSetStatRepository.findByPlayerId(mid.getId()).getFirst();
		assertThat(savedMid.getId().getPosition()).isEqualTo(Position.MID);
		assertThat(savedMid.getKills()).isEqualTo(5);
		assertThat(savedMid.getDamageTakenPm()).isEqualByComparingTo("612.4");
		assertThat(savedMid.getGoldAt15()).isNull();
		assertThat(savedMid.getPog()).isFalse();
		assertThat(savedMid.getChampion().getChampionName()).isEqualTo("TestAhri");
		assertThat(savedMid.getTeamSetStat().getMatchSet().getMatch().getStage().getStageCode()).isEqualTo("TST");

		assertThat(setBanRepository.findByIdMatchIdAndIdSetNo(match.getId(), 1))
				.extracting(ban -> ban.getChampion().getChampionName()).containsExactly("TestAzir");
		assertThat(playerRepository.findById(jungle.getId()).orElseThrow().getMainPosition()).isEqualTo(Position.JGL);
		assertThat(seasonStatRepository.findById(mid.getId()).orElseThrow().getOverallScore())
				.isEqualByComparingTo("81.25");
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getBestOf()).isEqualTo(3);
	}
}
