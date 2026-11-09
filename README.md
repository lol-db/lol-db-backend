# RIFT DATA Backend

LCK 경기 기록 및 선수 통계 관리 서비스의 백엔드입니다.

Spring Boot 4.1.2 / Java 21 / Spring Data JPA / MySQL 8.0

## 로컬 실행

### 1. MySQL 켜기 (Docker, 권장)

```bash
docker compose up -d
```

MySQL 8.0이 뜨고, 처음 켤 때 `src/main/resources/db/schema.sql`이 자동으로 실행돼 테이블이 만들어집니다. `schema.sql`을 바꾼 뒤에는 `docker compose down -v`로 데이터를 지우고 다시 켜야 반영됩니다.

`.env`가 없으면 root 비밀번호는 `root`이고, 서버도 기본값 `root` / `root`로 접속하므로 바로 됩니다. `.env`를 쓸 때는 `DB_USERNAME`에 `root`가 아닌 이름(예: `riftdata`)을 넣습니다. compose가 그 이름으로 일반 계정을 만드는데, MySQL 이미지는 이 계정 이름으로 `root`를 허용하지 않아 컨테이너가 뜨지 않습니다.

### 1-1. Docker 없이 직접 설치한 MySQL을 쓸 때

```bash
mysql -h 127.0.0.1 -u root -p -e "CREATE DATABASE riftdata CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysql -h 127.0.0.1 -u root -p riftdata < src/main/resources/db/schema.sql
```

### 2. 서버 실행

```bash
./gradlew bootRun   # 서버 실행
./gradlew build     # 빌드와 테스트 (MySQL 필요)
```

접속 정보는 환경변수 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`로 바꿀 수 있고, 없으면 `localhost:3306/riftdata`, `root` / `root`를 씁니다. SQL 로그를 보려면 `JPA_SHOW_SQL=true`를 줍니다.

`ddl-auto: validate`라서 엔티티와 테이블이 다르면 서버가 뜨지 않습니다. 테이블 구조를 바꿀 때는 `schema.sql`과 엔티티를 같이 고칩니다.

## 패키지 구조

```
com.riftdata
├── domain
│   ├── team       Team
│   ├── player     Player, PlayerSeasonStat
│   ├── champion   Champion
│   ├── match      Stage, LckMatch, MatchSet
│   └── record     TeamSetStat, PlayerSetStat, SetBan
│       (각 도메인 아래 entity / repository / service / controller / dto)
└── global
    ├── common     Position, Side (코드값 enum)
    ├── config
    └── exception
```

## CI

`main`에 push하거나 `main` 대상 PR을 올리면 GitHub Actions가 MySQL 8.0을 띄우고 `schema.sql`로 테이블을 만든 뒤 빌드와 테스트를 실행합니다. `main`에 push한 빌드의 jar는 Actions 실행 결과에 14일 동안 보관됩니다.

배포 단계는 아직 없습니다.
