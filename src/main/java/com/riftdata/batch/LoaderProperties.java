package com.riftdata.batch;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CSV 적재 설정 (application-loader.yml의 loader.*). 실행할 때 --loader.file=... 처럼 바꿀 수 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "loader")
public class LoaderProperties {

	/** 적재할 Oracle's Elixir CSV 경로 */
	private String file;

	/** true면 기존 경기 데이터(경기·세트·기록·시즌 지표)를 모두 지우고 다시 적재한다. 팀·선수·챔피언은 남긴다. */
	private boolean replace = false;

	/** true면 CSV 없이 세트 점수·POG·시즌 지표만 다시 계산한다. */
	private boolean recalculateOnly = false;

	/** 원천의 league 값 (BR-001: LCK만) */
	private String league = "LCK";

	/** 적재할 시즌 연도. 경기 일시(한국 시간)가 이 연도인 경기만 적재한다. */
	private int seasonYear = 2026;

	/** 원천 date 컬럼의 시간대. Oracle's Elixir는 UTC로 기록한다. */
	private ZoneId sourceZone = ZoneId.of("UTC");

	/** 원천 teamname → 팀코드(최대 4자). 매핑이 없는 팀이 있으면 사전 검증에서 오류로 알린다. */
	private Map<String, String> teamCodes = new LinkedHashMap<>();
}
