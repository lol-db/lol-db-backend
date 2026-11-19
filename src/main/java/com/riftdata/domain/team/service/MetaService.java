package com.riftdata.domain.team.service;

import com.riftdata.domain.match.entity.Stage;
import com.riftdata.domain.team.config.SeasonSettings;
import com.riftdata.domain.team.dto.MetaResponse;
import com.riftdata.domain.team.dto.SearchResponse;
import com.riftdata.domain.team.dto.SearchResponse.ChampionItem;
import com.riftdata.domain.team.dto.SearchResponse.PlayerItem;
import com.riftdata.domain.team.dto.TeamResponse;
import com.riftdata.domain.team.repository.MetaQueryRepository;
import com.riftdata.global.common.KstTime;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시즌 메타(API-01)와 통합 검색(API-02). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetaService {

	static final int DEFAULT_SEARCH_LIMIT = 5;
	static final int MAX_SEARCH_LIMIT = 20;

	private final MetaQueryRepository metaQueryRepository;
	private final SeasonSettings seasonSettings;
	private final Clock clock;

	public MetaResponse getMeta() {
		List<Stage> stages = metaQueryRepository.findStages();
		LocalDate today = LocalDate.now(clock);
		MetaResponse.Stage current = stages.stream()
				.filter(s -> !today.isBefore(s.getStartDate()) && !today.isAfter(s.getEndDate()))
				.findFirst()
				.map(MetaService::toStage)
				.orElse(null);
		LocalDate start = stages.stream().map(Stage::getStartDate).min(Comparator.naturalOrder()).orElse(null);
		LocalDate end = stages.stream().map(Stage::getEndDate).max(Comparator.naturalOrder()).orElse(null);

		MetaResponse.Season season = new MetaResponse.Season(seasonSettings.getName(), start, end,
				(int) metaQueryRepository.countTeams(), seasonSettings.getPlayoffTeamCount(), current);
		LocalDateTime lastCalculatedAt = metaQueryRepository.findLastCalculatedAt();
		return new MetaResponse(
				season,
				sortPatches(metaQueryRepository.findPatches()),
				stages.stream().map(MetaService::toStage).toList(),
				seasonSettings.getMinSetsForGrade(),
				lastCalculatedAt == null ? null : KstTime.toOffset(lastCalculatedAt),
				seasonSettings.getDataSource());
	}

	/**
	 * 선수 닉네임, 팀코드, 챔피언 이름에서 q를 찾는다. 대소문자를 무시하고 앞부분이 맞는 결과가 먼저다.
	 * q가 비었거나 limit이 1~20 밖이면 400 (COMMON_001).
	 */
	public SearchResponse search(String q, Integer limit) {
		String keyword = q == null ? "" : q.strip();
		int size = limit == null ? DEFAULT_SEARCH_LIMIT : limit;
		if (keyword.isEmpty() || size < 1 || size > MAX_SEARCH_LIMIT) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		String escaped = escapeLike(keyword.toLowerCase(Locale.ROOT));
		String prefix = escaped + "%";
		String contains = "%" + escaped + "%";
		Pageable page = PageRequest.of(0, size);

		List<PlayerItem> players = metaQueryRepository.searchPlayers(prefix, contains, page).stream()
				.map(p -> new PlayerItem(p.playerId(), p.nickname(), p.position(), p.teamId(), p.teamCode()))
				.toList();
		List<TeamResponse> teams = metaQueryRepository.searchTeams(prefix, contains, page).stream()
				.map(TeamResponse::from)
				.toList();
		List<ChampionItem> champions = metaQueryRepository.searchChampions(prefix, contains, page).stream()
				.map(c -> new ChampionItem(c.championId(), c.name()))
				.toList();
		return new SearchResponse(players, teams, champions);
	}

	private static MetaResponse.Stage toStage(Stage stage) {
		return new MetaResponse.Stage(stage.getId(), stage.getName(), stage.getRegular());
	}

	/** LIKE의 특수문자(%, _)와 escape 문자(!)를 글자 그대로 찾도록 바꾼다. */
	static String escapeLike(String keyword) {
		return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	/** "26.10"이 "26.9" 뒤에 오도록 숫자 단위로 정렬한다. 숫자가 아닌 부분은 글자순. */
	static List<String> sortPatches(List<String> patches) {
		Comparator<String> byNumbers = (a, b) -> {
			String[] x = a.split("\\.");
			String[] y = b.split("\\.");
			for (int i = 0; i < Math.min(x.length, y.length); i++) {
				int c = comparePart(x[i], y[i]);
				if (c != 0) {
					return c;
				}
			}
			return Integer.compare(x.length, y.length);
		};
		return patches.stream().distinct().sorted(byNumbers).toList();
	}

	private static int comparePart(String a, String b) {
		boolean numeric = !a.isEmpty() && !b.isEmpty()
				&& Arrays.stream(new String[] {a, b}).allMatch(s -> s.chars().allMatch(Character::isDigit));
		return numeric ? Long.compare(Long.parseLong(a), Long.parseLong(b)) : a.compareTo(b);
	}
}
