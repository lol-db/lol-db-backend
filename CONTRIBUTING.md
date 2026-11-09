# 함께 개발하는 방법

## 1. 오류가 생겼을 때

공통 오류 처리는 **여러 기능에서 생긴 문제를 같은 안내문 모양으로 바꾸는 창구**입니다.

예를 들어 선수 조회 기능은 선수가 없으면 아래처럼 작성합니다.

```java
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;

Player player = playerRepository.findById(playerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PLAYER_NOT_FOUND));
```

공통 처리기가 HTTP 404와 다음 JSON을 보냅니다.

```json
{
  "status": 404,
  "code": "PLAYER_001",
  "message": "존재하지 않는 선수입니다."
}
```

- `status`: HTTP 오류 종류입니다. 400은 잘못된 요청, 404는 없음, 500은 서버 내부 오류입니다.
- `code`: 프론트엔드가 오류를 구분할 이름입니다.
- `message`: 사용자에게 보여 줄 설명입니다.

`try-catch`로 오류를 잡고 무시하지 마세요. 예상한 업무 오류는 `BusinessException`을 사용합니다. 예상하지 못한 오류는 서버 로그에 원인을 남기고, 응답에는 공통 500 안내문만 보냅니다.

이 처리는 Spring MVC의 요청 처리에 적용됩니다. 향후 인증 필터나 별도 배치 작업을 추가하면 해당 경로의 오류 처리도 연결해야 합니다.

## 2. 파일별 역할

모두 `src/main/java/com/riftdata/global/exception`에 있습니다.

| 파일 | 하는 일 |
| --- | --- |
| `ErrorCode.java` | 오류 번호, HTTP 상태, 안내 문구 목록 |
| `BusinessException.java` | 기능 코드에서 업무 오류를 전달하는 도구 |
| `ErrorResponse.java` | 응답의 세 항목을 담는 틀 |
| `GlobalExceptionHandler.java` | 오류를 받아 정해진 응답으로 바꾸는 처리기 |

입력 검증에는 요청 DTO의 `@NotBlank` 등과 컨트롤러의 `@Valid`를 사용합니다. 검증 문구는 사용자용으로 작성하며 비밀번호 등 실제 입력값을 넣지 않습니다. 잘못된 JSON·자료형·필수 요청값도 400으로 처리합니다.

## 3. 처음 등록한 오류 코드

접두사는 팀에서 지정한 패키지에 맞췄습니다. 아래 세부 번호와 문구는 이번 공통 설정에서 추가한 것으로, 프론트엔드 API 명세와 함께 맞춰 사용합니다.

| 상수 | 코드 | HTTP | 의미 |
| --- | --- | --- | --- |
| `INVALID_INPUT` | COMMON_001 | 400 | 잘못된 입력 |
| `METHOD_NOT_ALLOWED` | COMMON_002 | 405 | 지원하지 않는 HTTP 메서드 |
| `RESOURCE_NOT_FOUND` | COMMON_003 | 404 | 없는 주소 |
| `UNSUPPORTED_MEDIA_TYPE` | COMMON_004 | 415 | 지원하지 않는 요청 형식 |
| `NOT_ACCEPTABLE` | COMMON_005 | 406 | 지원하지 않는 응답 형식 |
| `REQUEST_REJECTED` | COMMON_006 | 원래 4xx 유지 | 그 밖의 Spring MVC 요청 오류 |
| `INTERNAL_SERVER_ERROR` | COMMON_999 | 500 등 원래 5xx 유지 | 서버 내부 오류 |
| `TEAM_NOT_FOUND` | TEAM_001 | 404 | 팀 없음 |
| `PLAYER_NOT_FOUND` | PLAYER_001 | 404 | 선수 없음 |
| `CHAMPION_NOT_FOUND` | CHAMPION_001 | 404 | 챔피언 없음 |
| `STAGE_NOT_FOUND` | MATCH_001 | 404 | 스테이지 없음 |
| `MATCH_NOT_FOUND` | MATCH_002 | 404 | 경기 없음 |
| `SET_NOT_FOUND` | MATCH_003 | 404 | 세트 없음 |
| `RECORD_NOT_FOUND` | RECORD_001 | 404 | 팀·선수 세트 기록 없음 |
| `BAN_NOT_FOUND` | RECORD_002 | 404 | 밴 기록 없음 |

새 오류가 필요하면 `ErrorCode`에 항목을 추가하고 같은 PR에 넣습니다. 기존 코드를 다른 뜻으로 재사용하지 않습니다. `MATCH_`는 스테이지·경기·세트, `RECORD_`는 세트 기록·밴에 사용합니다.

## 4. 개인 설정과 공통 설정

- `.env.example`을 복사한 `.env`에 자신의 DB 계정과 비밀번호를 채웁니다.
- DB 변수는 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 다섯 개입니다.
- 실제 `.env`와 `*.env`는 올리지 않고, `.env.example`만 공유합니다.
- 공통 DB 연결과 엔티티 검사는 기존 `application.yml`에 유지합니다.
- SQL 출력은 `application-local.yml`에 있습니다. 기본 프로필은 `local`입니다.
- 현재 배포용 프로필은 없습니다. DB 설정을 바꿀 때는 DB 연결 담당자와 먼저 맞춥니다.

설정 파일이 `.env`를 읽는 기준은 실행한 폴더입니다. IDE에서도 작업 폴더를 프로젝트 루트로 지정하세요. 상세 실행 순서는 [README.md](README.md)에 있습니다.

## 5. 변경 사항 공유

1. 현재 팀의 통합 브랜치는 `main`입니다. 선행 PR이 있다면 그 작업을 먼저 반영한 기준에서 개인 브랜치를 만듭니다.
2. 변경한 코드와 설명을 한국어로 작성합니다.
3. 실제 실행한 테스트와 결과를 PR 양식에 적습니다. 실행하지 않은 검사는 완료로 표시하지 않습니다.
4. `git status`와 올릴 변경 내용을 확인해 실제 비밀번호·`.env`가 포함되지 않았는지 확인합니다.
5. PR 검토를 요청하고, 기존 CI의 빌드·테스트 결과를 확인합니다.

PR 양식은 `.github/PULL_REQUEST_TEMPLATE.md`, 기능 요청·오류 제보 양식은 `.github/ISSUE_TEMPLATE`에 있습니다. 이번 작업에서는 기존 `.github/workflows/ci.yml`을 사용합니다.

## 참고한 공식 문서

- [Spring Boot 외부 설정과 우선순위](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring MVC 공통 예외 처리](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
