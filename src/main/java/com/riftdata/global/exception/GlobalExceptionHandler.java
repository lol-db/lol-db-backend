package com.riftdata.global.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception) {
		ErrorCode code = exception.getErrorCode();
		log.warn("BusinessException: {}", code.getCode());
		return ResponseEntity.status(code.getStatus()).contentType(MediaType.APPLICATION_JSON)
				.body(ErrorResponse.of(code));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
		// 검증 대상의 실제 값이나 내부 메서드 이름을 응답에 노출하지 않는다.
		return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
				.body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
	}

	/** Spring MVC가 정한 HTTP 상태와 Allow 같은 헤더는 유지하고 본문만 통일한다. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(
			Exception exception, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ErrorCode code = codeForStatus(statusCode);
		String message = code.getMessage();
		if (exception instanceof MethodArgumentNotValidException validationException) {
			message = validationMessage(validationException);
		}
		if (statusCode.is5xxServerError()) {
			log.error("Spring MVC request processing failed", exception);
		}

		// 예외의 원문/ProblemDetail에는 내부 정보가 들어갈 수 있으므로 그대로 반환하지 않는다.
		ErrorResponse response = new ErrorResponse(statusCode.value(), code.getCode(), message);
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.putAll(headers);
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		return super.handleExceptionInternal(exception, response, responseHeaders, statusCode, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
		log.error("Unhandled exception", exception);
		return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
				.body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
	}

	private ErrorCode codeForStatus(HttpStatusCode statusCode) {
		return switch (statusCode.value()) {
			case 400 -> ErrorCode.INVALID_INPUT;
			case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 406 -> ErrorCode.NOT_ACCEPTABLE;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> statusCode.is4xxClientError()
					? ErrorCode.REQUEST_REJECTED : ErrorCode.INTERNAL_SERVER_ERROR;
		};
	}

	private String validationMessage(MethodArgumentNotValidException exception) {
		return exception.getBindingResult().getFieldErrors().stream()
				.filter(error -> !error.isBindingFailure() && error.getDefaultMessage() != null)
				.findFirst()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.orElse(ErrorCode.INVALID_INPUT.getMessage());
	}
}
