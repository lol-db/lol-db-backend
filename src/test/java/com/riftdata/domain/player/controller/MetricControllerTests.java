package com.riftdata.domain.player.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MetricControllerTests {

	@Autowired MockMvc mockMvc;

	@Test
	@DisplayName("지표 사전은 명세서의 지표 20개를 영역 순으로 준다")
	void listsTwentyMetricsInAreaOrder() throws Exception {
		mockMvc.perform(get("/api/v1/metrics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(20))
				.andExpect(jsonPath("$[*].code").value(contains(
						"KDA", "KP", "FB_PCT", "KA15",
						"DPM", "DMG_PCT", "DMG_PER_GOLD",
						"GD15", "CSD15", "XPD15",
						"CSM", "GPM", "GOLD_PCT",
						"VSPM", "WPM", "WCPM", "CW",
						"D10", "FD_PCT", "D15")))
				.andExpect(jsonPath("$[0].abbr").value("KDA"))
				.andExpect(jsonPath("$[0].area").value("COMBAT"))
				.andExpect(jsonPath("$[0].direction").value(1))
				.andExpect(jsonPath("$[0].unit").value(nullValue()))
				.andExpect(jsonPath("$[0].formula").isNotEmpty())
				.andExpect(jsonPath("$[?(@.code == 'GD15')].abbr").value(contains("GD@15")))
				.andExpect(jsonPath("$[?(@.code == 'KP')].unit").value(contains("%")))
				// 생존 영역 지표는 모두 낮을수록 좋다
				.andExpect(jsonPath("$[?(@.area == 'SURVIVAL')].direction").value(everyItem(is(-1))))
				.andExpect(jsonPath("$[?(@.area != 'SURVIVAL')].direction").value(everyItem(is(1))));
	}
}
