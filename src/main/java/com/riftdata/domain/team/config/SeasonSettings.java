package com.riftdata.domain.team.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DB에 저장하지 않는 시즌 설정값 (API 명세서 API-01). 기본값은 2026 LCK이고, 바꿀 때는
 * application.yml이나 환경변수로 app.season.* 값을 준다.
 */
@Getter
@Component
public class SeasonSettings {

	private final String name;
	private final int playoffTeamCount;
	private final int minSetsForGrade;
	private final String dataSource;

	public SeasonSettings(
			@Value("${app.season.name:2026 LCK}") String name,
			@Value("${app.season.playoff-team-count:6}") int playoffTeamCount,
			@Value("${app.season.min-sets-for-grade:10}") int minSetsForGrade,
			@Value("${app.season.data-source:Oracle's Elixir (2026 LCK)}") String dataSource) {
		this.name = name;
		this.playoffTeamCount = playoffTeamCount;
		this.minSetsForGrade = minSetsForGrade;
		this.dataSource = dataSource;
	}
}
