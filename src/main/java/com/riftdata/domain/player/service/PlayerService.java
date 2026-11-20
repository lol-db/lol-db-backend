package com.riftdata.domain.player.service;

import com.riftdata.domain.player.dto.PlayerDetailResponse;
import com.riftdata.domain.player.dto.PlayerDetailResponse.Filter;
import com.riftdata.domain.player.dto.PlayerDetailResponse.Summary;
import com.riftdata.domain.player.dto.PlayerProfile;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.domain.player.repository.PlayerSeasonRepository;
import com.riftdata.domain.player.repository.PlayerStatisticsRepository;
import com.riftdata.domain.player.repository.PlayerStatisticsRepository.CsSample;
import com.riftdata.domain.player.repository.PlayerStatisticsRepository.SetRecord;
import com.riftdata.global.common.Decimals;
import com.riftdata.global.common.Rating;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {
	private final PlayerRepository playerRepository;
	private final PlayerSeasonRepository playerSeasonRepository;
	private final PlayerStatisticsRepository statisticsRepository;

	/** 선수 한 명의 기본정보를 조회하는 내부 기능입니다. */
	public PlayerProfile getProfile(Long playerId) {
		return PlayerProfile.from(findPlayer(playerId));
	}

	/** API-13의 필터와 요약 통계를 구성합니다. DB에 없는 점수를 임의로 만들지 않습니다. */
	public PlayerDetailResponse getDetail(Long playerId, String patch, Long championId) {
		String normalizedPatch = normalizePatch(patch);
		if (championId != null && championId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		Player player = findPlayer(playerId);
		PlayerProfile profile = PlayerProfile.from(player);
		List<SetRecord> records = statisticsRepository.findRecords(playerId, normalizedPatch, championId);
		List<Long> teamSetIds = records.stream().map(SetRecord::teamSetId).distinct().toList();
		Map<Long, Long> teamKillsBySet = statisticsRepository.findTeamKills(teamSetIds);
		List<CsSample> comparison = statisticsRepository.findPositionCsSamples(
				player.getPosition(), normalizedPatch, championId);

		Summary summary = summarize(records, teamKillsBySet, comparison);
		LocalDate firstDay = records.stream().map(row -> row.playedAt().toLocalDate())
				.min(LocalDate::compareTo).orElse(null);
		LocalDate lastDay = records.stream().map(row -> row.playedAt().toLocalDate())
				.max(LocalDate::compareTo).orElse(null);
		boolean filtered = normalizedPatch != null || championId != null;
		String grade = seasonGrade(playerId, summary.sets(), filtered);
		return new PlayerDetailResponse(profile.playerId(), profile.name(), profile.position(),
				profile.team(), new Filter(normalizedPatch, championId, firstDay, lastDay),
				summary, grade, grade != null);
	}

	private Player findPlayer(Long playerId) {
		if (playerId == null || playerId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return playerRepository.findById(playerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLAYER_NOT_FOUND));
	}

	private static String normalizePatch(String patch) {
		if (patch == null || patch.isBlank()) {
			return null;
		}
		String normalized = patch.strip();
		// 패치 버전 컬럼의 최대 길이는 15자입니다.
		if (normalized.length() > 15 || normalized.chars().anyMatch(Character::isISOControl)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return normalized;
	}

	private String seasonGrade(Long playerId, int sets, boolean filtered) {
		// 필터 통계로 시즌 등급을 재계산하지 않습니다. 시즌 점수가 미적재 상태여도 null입니다.
		if (filtered || sets < 10) {
			return null;
		}
		return playerSeasonRepository.findByPlayerId(playerId)
				.filter(season -> season.getSets() >= 10)
				.map(season -> PlayerGrade.fromPoints(season.getTotalPoints()))
				.orElse(null);
	}

	private static Summary summarize(List<SetRecord> records, Map<Long, Long> teamKillsBySet,
			List<CsSample> comparison) {
		int sets = records.size();
		int wins = (int) records.stream().filter(SetRecord::win).count();
		long kills = records.stream().mapToLong(SetRecord::kills).sum();
		long deaths = records.stream().mapToLong(SetRecord::deaths).sum();
		long assists = records.stream().mapToLong(SetRecord::assists).sum();
		// 당시 소속 팀 전체의 킬입니다. 팀원에게 대상 선수의 챔피언 필터를 적용하지 않습니다.
		long teamKills = records.stream().map(SetRecord::teamSetId).distinct()
				.mapToLong(id -> teamKillsBySet.getOrDefault(id, 0L)).sum();
		List<Double> ratings = records.stream().map(SetRecord::totalPoints)
				.map(Rating::fromPoints).filter(Objects::nonNull).toList();
		Double averageRating = Rating.average(ratings);
		Double bestRating = ratings.stream().max(Double::compareTo).orElse(null);
		int pogCount = (int) records.stream().filter(SetRecord::pog).count();
		List<CsSample> ownCs = records.stream()
				.map(row -> new CsSample(row.cs(), row.durationSeconds())).toList();

		return new Summary(sets, wins, Decimals.rate(wins, sets), kills, deaths, assists,
				sets == 0 ? null : Decimals.kda(kills, deaths, assists),
				Decimals.rate(kills + assists, teamKills), teamKills,
				averageCs(ownCs), averageCs(comparison), averageRating, bestRating, pogCount);
	}

	private static Double averageCs(List<CsSample> samples) {
		// 화면설계서와 같이 세트별 분당 CS의 산술평균입니다. 0초 기록은 계산에서 제외합니다.
		OptionalDouble average = samples.stream().filter(sample -> sample.durationSeconds() > 0)
				.mapToDouble(sample -> sample.cs() * 60.0 / sample.durationSeconds()).average();
		return average.isEmpty() ? null : Decimals.stat(average.getAsDouble());
	}
}
