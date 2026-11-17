package com.riftdata.support;

import com.riftdata.domain.champion.entity.Champion;
import com.riftdata.domain.match.entity.LckMatch;
import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.match.entity.Stage;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.record.entity.Ban;
import com.riftdata.domain.record.entity.PlayerSet;
import com.riftdata.domain.record.entity.TeamSet;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 경기 도메인 테스트 데이터. 실제 MySQL에 넣고 테스트의 @Transactional 롤백으로 지운다.
 * CSV로 올린 실데이터와 UNIQUE가 겹치지 않도록 팀·선수·챔피언 이름에 ZZ 접두사를 쓴다.
 *
 * <pre>
 * 2026-04-26 17:00  ZZA 2 : 1 ZZB (bo3, 종료)
 *   1세트 ZZA 승 · POG ZZA_MID 88점
 *   2세트 ZZB 승 · POG ZZB_MID 80점
 *   3세트 ZZA 승 · POG ZZA_MID 95점
 * 2026-04-26 20:00  ZZB vs ZZA (bo3, 예정, 세트 없음)
 *
 * 세트 공통: 30분(1800초), 홀수 세트는 ZZA 블루, 짝수 세트는 ZZB 블루
 * 선수마다 2/1/3, 챔피언 피해 15,000, 골드 11,000, CS 200+10, 시야 30
 * 시점 골드: 분 × 300 (이긴 팀은 +20), 25분 기록은 없음 → 골드 차는 10·15·20분만
 * 팀 오브젝트: 이긴 팀 포탑 9·용 3·유충 4·바론 1·억제기 2, 진 팀 포탑 3·용 1·나머지 0
 * 1세트만 밴: 팀마다 ZZBan 챔피언 2개
 * </pre>
 */
public final class MatchFixture {

	public static final LocalDate MATCH_DAY = LocalDate.of(2026, 4, 26);

	public record Data(
			Team teamA,
			Team teamB,
			List<Player> playersA,
			List<Player> playersB,
			Stage stage,
			LckMatch finished,
			List<MatchSet> finishedSets,
			LckMatch scheduled) {
	}

	private MatchFixture() {
	}

	public static Data create(EntityManager em) {
		Team teamA = persist(em, Team.builder().code("ZZA").build());
		Team teamB = persist(em, Team.builder().code("ZZB").build());
		List<Player> playersA = roster(em, teamA);
		List<Player> playersB = roster(em, teamB);
		List<Champion> champions = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			champions.add(persist(em, Champion.builder().name("ZZChamp" + i).build()));
		}
		List<Champion> banChampions = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			banChampions.add(persist(em, Champion.builder().name("ZZBan" + i).build()));
		}
		Stage stage = persist(em, Stage.builder()
				.name("ZZ Regular").stageOrder(98) // TINYINT 매핑이라 127 이하, EntityMappingTests는 99 사용
				.startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2026, 6, 14)).regular(true).build());

		LckMatch finished = persist(em, LckMatch.builder()
				.matchTime(MATCH_DAY.atTime(17, 0)).sets(3).team1(teamA).team2(teamB).stage(stage).build());
		LckMatch scheduled = persist(em, LckMatch.builder()
				.matchTime(MATCH_DAY.atTime(20, 0)).sets(3).team1(teamB).team2(teamA).stage(stage).build());

		// 세트 번호, 이긴 팀이 A인지, POG 총 점수
		Object[][] results = {{1, true, 88}, {2, false, 80}, {3, true, 95}};
		List<MatchSet> sets = new ArrayList<>();
		for (Object[] r : results) {
			int setNumber = (int) r[0];
			boolean aWins = (boolean) r[1];
			int pogPoints = (int) r[2];
			MatchSet set = persist(em, MatchSet.builder()
					.match(finished).setNumber(setNumber).gameId("ZZ-GAME-" + setNumber).version("26.08")
					.playTime(LocalTime.of(0, 30, 0)).build());
			sets.add(set);
			// 세트마다 블루·레드를 번갈아 맡긴다
			Side sideA = setNumber % 2 == 1 ? Side.BLUE : Side.RED;
			Side sideB = sideA == Side.BLUE ? Side.RED : Side.BLUE;
			TeamSet tsA = persist(em, teamSet(set, teamA, sideA, aWins));
			TeamSet tsB = persist(em, teamSet(set, teamB, sideB, !aWins));
			lineup(em, tsA, playersA, champions.subList(0, 5), aWins, aWins ? pogPoints : null);
			lineup(em, tsB, playersB, champions.subList(5, 10), !aWins, aWins ? null : pogPoints);
			if (setNumber == 1) {
				for (int order = 1; order <= 2; order++) {
					persist(em, Ban.builder().teamSet(tsA).banOrder(order).champion(banChampions.get(order - 1)).build());
					persist(em, Ban.builder().teamSet(tsB).banOrder(order).champion(banChampions.get(order + 1)).build());
				}
			}
		}

		em.flush();
		em.clear();
		return new Data(teamA, teamB, playersA, playersB, stage, finished, sets, scheduled);
	}

	private static List<Player> roster(EntityManager em, Team team) {
		List<Player> players = new ArrayList<>();
		for (Position position : Position.values()) {
			players.add(persist(em, Player.builder()
					.team(team).nickname(team.getCode() + "_" + position.name()).position(position).build()));
		}
		return players;
	}

	private static TeamSet teamSet(MatchSet set, Team team, Side side, boolean win) {
		return TeamSet.builder().matchSet(set).team(team).side(side).victory(win)
				.towers(win ? 9 : 3).dragons(win ? 3 : 1).voidGrubs(win ? 4 : 0).barons(win ? 1 : 0)
				.inhibitors(win ? 2 : 0)
				.build();
	}

	/** 선수 5명의 세트 기록. pogPoints가 있으면 미드에게 POG를 준다. */
	private static void lineup(EntityManager em, TeamSet teamSet, List<Player> players,
			List<Champion> champions, boolean win, Integer pogPoints) {
		int goldBonus = win ? 20 : 0;
		for (int i = 0; i < players.size(); i++) {
			Player player = players.get(i);
			boolean pog = pogPoints != null && player.getPosition() == Position.MID;
			persist(em, PlayerSet.builder()
					.teamSet(teamSet).position(player.getPosition()).player(player).champion(champions.get(i))
					.kills(2).deaths(1).assists(3).damageToChampions(15_000).totalGolds(11_000)
					.minionKills(200).monsterKills(10).visionPoints(30)
					.goldAt10(10 * 300 + goldBonus).goldAt15(15 * 300 + goldBonus).goldAt20(20 * 300 + goldBonus)
					.totalPoints(pog ? pogPoints : 50).pog(pog)
					.build());
		}
	}

	private static <T> T persist(EntityManager em, T entity) {
		em.persist(entity);
		return entity;
	}
}
