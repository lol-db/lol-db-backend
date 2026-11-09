# RIFT DATA Backend

LCK 경기 기록 및 선수 통계 관리 서비스의 백엔드입니다.

Spring Boot 4.1.2 / Java 21 / Spring Data JPA / MySQL 8.0

## 로컬 실행

### 0. Java 21과 개인 설정 준비

`java -version`으로 Java 21인지 확인합니다. 프로젝트 루트(`gradlew`가 있는 폴더)에서 실행하세요.

`.env`가 아직 없다면 `.env.example`을 복사합니다. Windows PowerShell에서는 다음 명령을 씁니다.

```powershell
Copy-Item .env.example .env
```

`.env`의 `DB_USERNAME`과 `DB_PASSWORD`를 **반드시 채운 뒤** 실행합니다. 기존 MySQL을 쓰면 그 DB의 계정 정보를 적고, Docker로 처음 만들면 일반 계정 이름(예: `riftdata`)과 새 비밀번호를 적습니다. 값을 따옴표로 감싸지 마세요.

Spring과 Docker Compose가 이 파일을 읽습니다. Spring에서는 운영체제 환경변수가 `.env`보다 우선합니다. 실제 `.env`는 Git에서 제외되고, 빈 견본인 `.env.example`만 공유합니다.

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

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다.

접속 정보는 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`로 바꿀 수 있고, 값 자체가 없으면 기존 기본값인 `localhost:3306/riftdata`, `root` / `root`를 씁니다. 빈 값은 기본값으로 대체되지 않으므로 `.env.example`을 복사한 뒤 계정과 비밀번호를 채워야 합니다.

별도 프로필을 지정하지 않으면 `local`로 실행합니다. SQL 출력은 `application-local.yml`에서 켜며, `JPA_SHOW_SQL=false` 환경변수로 끌 수 있습니다. 다른 프로필은 `SPRING_PROFILES_ACTIVE`로 지정합니다. 배포용 `prod` 설정은 아직 만들지 않았습니다.

`ddl-auto: validate`라서 엔티티와 테이블이 다르면 서버가 뜨지 않습니다. 테이블 구조를 바꿀 때는 `schema.sql`과 엔티티를 같이 고칩니다.

## 패키지 구조

```
com.riftdata
├── domain
│   ├── team       Team
│   ├── player     Player, PlayerSeason
│   ├── champion   Champion
│   ├── match      Stage, LckMatch, MatchSet
│   └── record     TeamSet, PlayerSet, Ban
│       (각 도메인 아래 entity / repository / service / controller / dto)
└── global
    ├── common     Position, Side (코드값 enum)
    ├── config
    └── exception
```

## CI

`main`에 push하거나 `main` 대상 PR을 올리면 GitHub Actions가 MySQL 8.0을 띄우고 `schema.sql`로 테이블을 만든 뒤 빌드와 테스트를 실행합니다. `main`에 push한 빌드의 jar는 Actions 실행 결과에 14일 동안 보관됩니다.

배포 단계는 아직 없습니다.

## 공통 오류 처리와 협업 양식

오류 응답은 `status`, `code`, `message` 세 항목으로 전달합니다. 사용 예시·오류 코드 목록·PR 작성 방법은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

공통 오류 처리만 빠르게 확인할 때는 DB 없이 아래 테스트를 실행할 수 있습니다.

```powershell
.\gradlew.bat test --tests com.riftdata.global.exception.GlobalExceptionHandlerTests
```

전체 검사는 기존과 같이 DB를 준비한 뒤 `.\gradlew.bat build`로 실행합니다.
