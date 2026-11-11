package com.riftdata.global.exception;

import java.util.Objects;
import lombok.Getter;

/** 서비스에서 예상한 업무 오류를 공통 응답 처리기로 전달한다. */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(Objects.requireNonNull(errorCode, "errorCode").getMessage());
		this.errorCode = errorCode;
	}
}
