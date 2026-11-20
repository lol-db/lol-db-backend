package com.riftdata.batch;

import com.riftdata.batch.OeGameReader.LoadValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * loader 프로필 실행기. 적재(또는 재계산)를 한 번 하고 끝난다. 실패하면 예외로 끝나 종료 코드가 1이다.
 *
 * <pre>
 * ./gradlew bootRun --args="--spring.profiles.active=loader --loader.file=/경로/파일.csv"
 * ./gradlew bootRun --args="--spring.profiles.active=loader --loader.file=/경로/파일.csv --loader.replace=true"
 * ./gradlew bootRun --args="--spring.profiles.active=loader --loader.recalculate-only=true"
 * </pre>
 */
@Slf4j
@Component
@Profile("loader")
@RequiredArgsConstructor
public class LoaderRunner implements CommandLineRunner {

	private final LoaderProperties properties;
	private final LckCsvLoader loader;
	private final SeasonRecalculator recalculator;

	@Override
	public void run(String... args) {
		try {
			if (properties.isRecalculateOnly()) {
				log.info("재계산만 실행합니다 (CSV 적재 없음)");
				recalculator.recalculate();
				return;
			}
			if (properties.getFile() == null || !Files.isRegularFile(Path.of(properties.getFile()))) {
				throw new LoadValidationException(java.util.List.of("CSV 파일이 없습니다. --loader.file=/경로/파일.csv 로 지정하세요: "
						+ properties.getFile()));
			}
			LckCsvLoader.Result result = loader.load(Path.of(properties.getFile()), properties.isReplace());
			log.info("""

					===== CSV 적재 결과 =====
					대상 경기 {}개 (이미 있던 경기 {}개 건너뜀) → 새로 적재 {}개, 새 시리즈 {}개
					제외한 행: 다른 리그 {}, 미완성 데이터 {}, 다른 연도 {}
					세트 점수 {}개, POG {}개, 시즌 지표 {}명 (등급 대상 {}명)
					""",
					result.csvGames(), result.skippedExisting(), result.loadedGames(), result.newMatches(),
					result.otherLeagueRows(), result.incompleteRows(), result.otherYearRows(),
					result.recalculation().scoredSets(), result.recalculation().pogs(),
					result.recalculation().seasonPlayers(), result.recalculation().gradedPlayers());
		} catch (LoadValidationException e) {
			log.error("{}\n아무것도 적재하지 않았습니다.", e.getMessage());
			throw new IllegalStateException("CSV 적재 중단: 사전 검증 실패");
		} catch (Exception e) {
			log.error("적재 실패, 전체 롤백했습니다: {}", e.getMessage(), e);
			throw new IllegalStateException("CSV 적재 실패: " + e.getMessage(), e);
		}
	}
}
