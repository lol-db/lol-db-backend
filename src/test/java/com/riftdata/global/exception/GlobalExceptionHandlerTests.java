package com.riftdata.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** DB를 연결하지 않고, 실제 HTTP 요청의 오류 응답과 상태 코드를 검사한다. */
class GlobalExceptionHandlerTests {

	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@AfterEach
	void closeValidator() {
		validator.close();
	}

	@Test
	@DisplayName("찾을 수 없는 팀은 팀 오류 코드와 404를 돌려준다")
	void businessExceptionUsesDomainError() throws Exception {
		expectError(mockMvc.perform(get("/__test/errors/business")), ErrorCode.TEAM_NOT_FOUND)
				.andExpect(jsonPath("$.message").value(ErrorCode.TEAM_NOT_FOUND.getMessage()));
	}

	@Test
	@DisplayName("존재하지 않는 주소는 공통 응답 형식의 404이다")
	void unknownPathIsNotInternalError() throws Exception {
		expectError(mockMvc.perform(get("/__test/errors/does-not-exist")), ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	@DisplayName("깨진 JSON은 서버 오류가 아닌 입력 오류 400이다")
	void malformedJsonIsBadRequest() throws Exception {
		expectError(mockMvc.perform(post("/__test/errors/players")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":")), ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("숫자 자리에 문자열을 보내면 입력 오류 400이다")
	void wrongParameterTypeIsBadRequest() throws Exception {
		expectError(mockMvc.perform(get("/__test/errors/number").param("id", "not-a-number")),
				ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("필수 요청값을 빠뜨리면 입력 오류 400이다")
	void missingParameterIsBadRequest() throws Exception {
		expectError(mockMvc.perform(get("/__test/errors/number")), ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("@Valid 검사에 실패해도 공통 응답 모양을 유지한다")
	void validationFailureUsesCommonResponse() throws Exception {
		expectError(mockMvc.perform(post("/__test/errors/players")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"   \"}")), ErrorCode.INVALID_INPUT)
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	@DisplayName("정상 입력은 예외 처리에 가로막히지 않고 성공한다")
	void validInputStillSucceeds() throws Exception {
		mockMvc.perform(post("/__test/errors/players")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Faker\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Faker"));
	}

	@Test
	@DisplayName("허용하지 않은 요청 방식은 405와 Allow 헤더를 유지한다")
	void wrongMethodPreservesAllowHeader() throws Exception {
		expectError(mockMvc.perform(post("/__test/errors/business")), ErrorCode.METHOD_NOT_ALLOWED)
				.andExpect(header().string(HttpHeaders.ALLOW, "GET"));
	}

	@Test
	@DisplayName("지원하지 않는 본문 형식은 415를 돌려준다")
	void unsupportedContentTypeIsNotInternalError() throws Exception {
		expectError(mockMvc.perform(post("/__test/errors/players")
				.contentType(MediaType.TEXT_PLAIN)
				.content("name=Faker")), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	@DisplayName("지원하지 않는 응답 형식을 요구하면 공통 오류 본문과 406을 돌려준다")
	void unsupportedAcceptHeaderStillReturnsStructuredError() throws Exception {
		expectError(mockMvc.perform(get("/__test/errors/number")
				.param("id", "1")
				.accept(MediaType.APPLICATION_XML)), ErrorCode.NOT_ACCEPTABLE);
	}

	@Test
	@DisplayName("예상하지 못한 오류는 500이며 내부 주소나 비밀번호를 노출하지 않는다")
	void unexpectedErrorDoesNotExposeInternalDetails() throws Exception {
		String response = expectError(mockMvc.perform(get("/__test/errors/unexpected")),
				ErrorCode.INTERNAL_SERVER_ERROR)
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
				.andReturn().getResponse().getContentAsString();

		assertThat(response).doesNotContain("internal-host", "example-password", "IllegalStateException");
	}

	private ResultActions expectError(ResultActions result, ErrorCode errorCode) throws Exception {
		return result.andExpect(status().is(errorCode.getStatus().value()))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(errorCode.getStatus().value()))
				.andExpect(jsonPath("$.code").value(errorCode.getCode()))
				.andExpect(jsonPath("$.message").isString());
	}

	/** 이 컨트롤러는 테스트 안에만 존재하며 실제 서비스에 등록되지 않는다. */
	@RestController
	static class ErrorTestController {

		@GetMapping("/__test/errors/business")
		void businessError() {
			throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
		}

		@GetMapping(value = "/__test/errors/number", produces = MediaType.APPLICATION_JSON_VALUE)
		long number(@RequestParam("id") long id) {
			return id;
		}

		@PostMapping(value = "/__test/errors/players", consumes = MediaType.APPLICATION_JSON_VALUE)
		PlayerRequest player(@Valid @RequestBody PlayerRequest request) {
			return request;
		}

		@GetMapping("/__test/errors/unexpected")
		void unexpectedError() {
			throw new IllegalStateException("jdbc:mysql://internal-host/private?password=example-password");
		}
	}

	public record PlayerRequest(@NotBlank(message = "선수 이름을 입력해 주세요.") String name) {
	}
}
