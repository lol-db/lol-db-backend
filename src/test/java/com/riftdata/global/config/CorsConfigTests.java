package com.riftdata.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;
import com.riftdata.global.exception.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** 실제 CORS 설정을 검사합니다. DB와 프론트 서버는 사용하지 않습니다. */
class CorsConfigTests {

	private static final String API_PATH = "/api/v1/__test/cors";
	private static final String ALLOWED_ORIGIN = "http://localhost:5173";
	private static final String OTHER_ALLOWED_ORIGIN = "http://127.0.0.1:5173";
	private static final String UNKNOWN_ORIGIN = "http://localhost:5999";

	private AnnotationConfigWebApplicationContext context;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		// 테스트용 주소이며, 쉼표와 공백 처리도 확인합니다.
		configureOrigins(" " + ALLOWED_ORIGIN + ", " + OTHER_ALLOWED_ORIGIN + " ");
	}

	@AfterEach
	void closeContext() {
		if (context != null) {
			context.close();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {ALLOWED_ORIGIN, OTHER_ALLOWED_ORIGIN})
	@DisplayName("등록한 주소의 조회를 허용하고 인증 정보 사용 허용은 켜지 않습니다")
	void allowsGetFromEachConfiguredOrigin(String origin) throws Exception {
		mockMvc.perform(get(API_PATH).header(HttpHeaders.ORIGIN, origin))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	@DisplayName("등록한 주소의 HEAD 요청을 허용합니다")
	void allowsHead() throws Exception {
		mockMvc.perform(head(API_PATH).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
	}

	@Test
	@DisplayName("주소 끝의 슬래시를 제거하여 적용합니다")
	void normalizesTrailingSlash() throws Exception {
		configureOrigins(ALLOWED_ORIGIN + "/");
		mockMvc.perform(get(API_PATH).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
	}

	@ParameterizedTest
	@ValueSource(strings = {"*", "null", "http://localhost:5173/players", "localhost:5173"})
	@DisplayName("잘못된 허용 주소는 시작할 때 오류로 알립니다")
	void rejectsInvalidOriginConfiguration(String origin) {
		assertThatThrownBy(() -> configureOrigins(origin))
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("사전 요청에 허용 주소와 요청 방식 및 헤더를 응답합니다")
	void answersGetPreflight() throws Exception {
		var response = mockMvc.perform(options(API_PATH)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
				.andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).isEqualTo("GET,HEAD");
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).isEqualToIgnoringCase("Content-Type");
	}

	@Test
	@DisplayName("등록하지 않은 주소의 조회와 사전 요청은 허용하지 않습니다")
	void rejectsUnknownOrigin() throws Exception {
		mockMvc.perform(get(API_PATH).header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

		mockMvc.perform(options(API_PATH)
				.header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("등록한 주소의 POST 요청도 CORS에서 허용하지 않습니다")
	void rejectsWriteMethod() throws Exception {
		mockMvc.perform(options(API_PATH)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

		mockMvc.perform(post(API_PATH).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("Authorization 헤더는 사전 요청에서 허용하지 않습니다")
	void rejectsUnapprovedRequestHeader() throws Exception {
		mockMvc.perform(options(API_PATH)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("허용 주소를 설정하지 않으면 다른 출처를 허용하지 않습니다")
	void unsetOriginsDoNotAllowCrossOriginRequests() throws Exception {
		configureOrigins(null);
		assertCrossOriginNotAllowed();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " , , "})
	@DisplayName("허용 주소가 비어 있으면 다른 출처를 허용하지 않습니다")
	void emptyOriginsDoNotAllowCrossOriginRequests(String origins) throws Exception {
		configureOrigins(origins);
		assertCrossOriginNotAllowed();
	}

	@Test
	@DisplayName("Origin이 없는 조회는 기존처럼 처리합니다")
	void preservesRequestsWithoutOrigin() throws Exception {
		mockMvc.perform(get(API_PATH))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	@DisplayName("CORS 설정은 API v1 경로에만 적용합니다")
	void doesNotAddCorsHeadersOutsideApiScope() throws Exception {
		mockMvc.perform(get("/outside-api/__test/cors").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("허용한 프론트는 선수 없음 오류 응답도 읽을 수 있습니다")
	void preservesCorsHeadersOnBusinessErrors() throws Exception {
		mockMvc.perform(get(API_PATH + "/missing-player").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isNotFound())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("PLAYER_001"));
	}

	private void assertCrossOriginNotAllowed() throws Exception {
		mockMvc.perform(get(API_PATH).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		mockMvc.perform(options(API_PATH)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	private void configureOrigins(String origins) {
		closeContext();
		context = new AnnotationConfigWebApplicationContext();
		context.setServletContext(new MockServletContext());
		MockEnvironment environment = new MockEnvironment();
		if (origins != null) {
			environment.setProperty("app.cors.allowed-origins", origins);
		}
		context.setEnvironment(environment);
		context.register(TestWebConfig.class);
		context.refresh();
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableWebMvc
	@Import({CorsConfig.class, CorsTestController.class, GlobalExceptionHandler.class})
	static class TestWebConfig {
	}

	/** 실제 서비스에 등록되지 않는 테스트 전용 경로입니다. */
	@RestController
	static class CorsTestController {

		@GetMapping({API_PATH, "/outside-api/__test/cors"})
		Map<String, String> read() {
			return Map.of("status", "ok");
		}

		// 경로가 있어도 CORS가 POST 요청을 거절하는지 확인합니다.
		@PostMapping(API_PATH)
		Map<String, String> writeForTestOnly() {
			return Map.of("status", "test-only");
		}

		@GetMapping(API_PATH + "/missing-player")
		void missingPlayer() {
			throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
		}
	}
}
