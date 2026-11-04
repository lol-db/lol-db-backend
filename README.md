# RIFT DATA Backend

LCK 경기 기록 및 선수 통계 관리 서비스의 백엔드입니다.

Spring Boot 4.1.2 / Java 21 / JdbcTemplate / MySQL 8.4

## 로컬 실행

MySQL 8.4에 `esports` 데이터베이스가 있어야 합니다. 접속 정보의 기본값은 `localhost:3306`, `root` / `root`이고 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 바꿀 수 있습니다.

```bash
./gradlew bootRun   # 서버 실행
./gradlew build     # 빌드와 테스트 (MySQL 필요)
```

## CI

`main`에 push하거나 `main` 대상 PR을 올리면 GitHub Actions가 MySQL 8.4를 띄우고 빌드와 테스트를 실행합니다. `main`에 push한 빌드의 jar는 Actions 실행 결과에 14일 동안 보관됩니다.

배포 단계는 아직 없습니다.
