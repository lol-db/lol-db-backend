package com.riftdata.global.exception;

/** 프론트엔드에 전달하는 공통 오류 응답. */
public record ErrorResponse(int status, String code, String message) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, errorCode.getMessage());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), message);
	}
}
