package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerPageResponse;
import com.riftdata.domain.player.dto.PlayerProfile;
import com.riftdata.domain.player.dto.PlayerRankingResponse;
import com.riftdata.domain.player.dto.PlayerRankingResponse.AreaScore;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;
import com.riftdata.domain.player.repository.PlayerRankingRepository;
import com.riftdata.domain.player.repository.PlayerRankingRepository.SetRating;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-12의 필터, 정렬, 표본 부족 표시와 페이지 처리를 담당합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerRankingService {
	private final PlayerRankingRepository repository;

	public PlayerPageResponse<PlayerRankingResponse> getRanking(
			Position position, String sort, boolean includeLowSample, String q, int page, int size) {
		PlayerQuerySupport.validatePage(page, size);
		String sortKey = validateSort(sort);
		String search = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
		Map<Long, PlayerSeason> seasons = repository.findSeasons().stream()
				.collect(Collectors.toMap(season -> season.getPlayer().getId(), season -> season));
		Map<Long, List<SetRating>> records = repository.findSeasonSetRatings().stream()
				.collect(Collectors.groupingBy(SetRating::playerId));

		List<PlayerRankingResponse> candidates = repository.findPlayers().stream()
				.filter(player -> position == null || player.getPosition() == position)
				// 문자열로 검색하여 %와 _도 검색어 그대로 처리합니다.
				.filter(player -> player.getNickname().toLowerCase(Locale.ROOT).contains(search))
				.map(player -> row(player, seasons.get(player.getId()),
						records.getOrDefault(player.getId(), List.of())))
				.filter(row -> includeLowSample || !row.lowSample())
				.sorted(Comparator.comparing(PlayerRankingResponse::lowSample)
						.thenComparing(row -> sortValue(row, sortKey),
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(PlayerRankingResponse::playerId))
				.toList();

		// 검색과 포지션 필터를 적용한 전체 결과에서 공동 순위를 매긴 다음 페이지를 자릅니다.
		List<PlayerRankingResponse> ranked = new ArrayList<>();
		Double previousValue = null;
		Integer previousRank = null;
		for (int i = 0; i < candidates.size(); i++) {
			PlayerRankingResponse row = candidates.get(i);
			Double value = sortValue(row, sortKey);
			Integer rank = null;
			if (!row.lowSample() && value != null) {
				rank = Objects.equals(value, previousValue) ? previousRank : i + 1;
				previousValue = value;
				previousRank = rank;
			}
			ranked.add(new PlayerRankingResponse(rank, row.playerId(), row.name(), row.position(),
					row.team(), row.sets(), row.avgRating(), row.overallScore(), row.grade(),
					row.areas(), row.lowSample()));
		}
		return PlayerPageResponse.from(ranked, page, size);
	}

	private static PlayerRankingResponse row(Player player, PlayerSeason season, List<SetRating> records) {
		int sets = records.size();
		boolean lowSample = sets < 10;
		boolean scoredSeason = !lowSample && season != null && season.getSets() >= 10;
		Integer overallPoints = scoredSeason && PlayerGrade.fromPoints(season.getTotalPoints()) != null
				? season.getTotalPoints() : null;
		List<Double> ratings = records.stream().map(SetRating::points).map(Rating::fromPoints)
				.filter(Objects::nonNull).toList();
		Double average = Rating.average(ratings);
		List<AreaScore> areas = Arrays.stream(PlayerArea.values()).map(area -> {
			Integer points = scoredSeason ? area.points(season) : null;
			return new AreaScore(area, points == null ? null : points.doubleValue(), PlayerGrade.fromPoints(points));
		}).toList();
		return new PlayerRankingResponse(null, player.getId(), player.getNickname(), player.getPosition(),
				new PlayerProfile.TeamInfo(player.getTeam().getId(), player.getTeam().getCode()),
				sets, average, overallPoints == null ? null : overallPoints.doubleValue(),
				PlayerGrade.fromPoints(overallPoints), areas, lowSample);
	}

	private static String validateSort(String sort) {
		if (sort == null || sort.isBlank()) return "overallScore";
		if (sort.equals("overallScore") || sort.equals("avgRating") || sort.equals("sets")) return sort;
		try {
			return PlayerArea.valueOf(sort).name();
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private static Double sortValue(PlayerRankingResponse row, String sort) {
		return switch (sort) {
			case "overallScore" -> row.overallScore();
			case "avgRating" -> row.avgRating();
			case "sets" -> (double) row.sets();
			default -> row.areas().stream().filter(area -> area.area().name().equals(sort))
					.map(AreaScore::score).filter(Objects::nonNull).findFirst().orElse(null);
		};
	}
}
