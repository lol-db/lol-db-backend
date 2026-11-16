package com.riftdata.global.config;

import com.riftdata.global.common.KstTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * '오늘'의 기준 시계. 날짜 파라미터가 없을 때 서비스는 LocalDate.now(clock)으로 한국 날짜를 구한다.
 * 테스트에서는 Clock.fixed(...)로 바꿔 끼워 날짜를 고정한다.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(KstTime.ZONE);
	}
}
