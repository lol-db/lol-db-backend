package com.riftdata.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통 요청 오류. 숫자는 한 번 사용하면 다른 의미로 재사용하지 않는다.
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 올바르지 않습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_002", "지원하지 않는 HTTP 메서드입니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "요청한 주소를 찾을 수 없습니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_004", "지원하지 않는 요청 데이터 형식입니다."),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "COMMON_005", "요청한 응답 형식을 지원하지 않습니다."),
	REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "COMMON_006", "요청을 처리할 수 없습니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다."),

	TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAM_001", "존재하지 않는 팀입니다."),
	PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAYER_001", "존재하지 않는 선수입니다."),
	CHAMPION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAMPION_001", "존재하지 않는 챔피언입니다."),

	// match 패키지: 스테이지, 경기, 세트
	STAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_001", "존재하지 않는 스테이지입니다."),
	MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_002", "존재하지 않는 경기입니다."),
	SET_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_003", "존재하지 않는 세트입니다."),

	// record 패키지: 팀·선수 세트 기록, 밴
	RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "RECORD_001", "존재하지 않는 세트 기록입니다."),
	BAN_NOT_FOUND(HttpStatus.NOT_FOUND, "RECORD_002", "존재하지 않는 밴 기록입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
