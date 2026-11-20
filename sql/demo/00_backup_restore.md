# 00. 시연 전 백업 · 시연 후 복원

- **보여 주는 것**: 시연(03·04)에서 바꾼 데이터를 시연 전 상태로 되돌리는 방법
- **실행 계정**: MySQL `root` (컨테이너 안의 `MYSQL_ROOT_PASSWORD`를 그대로 사용하므로 비밀번호를 입력하지 않음)
- **예상 결과**: 백업 파일 `sql/demo/backup/riftdata_before_demo.sql`이 생기고, 복원하면 `riftdata`의 모든 테이블이 백업 시점 내용으로 돌아감

명령은 프로젝트 루트(`gradlew`가 있는 폴더)에서 실행합니다. 컨테이너 이름은 `docker-compose.yml`의 `riftdata-mysql`입니다.

맥(zsh)과 Windows PowerShell에서 **같은 명령**을 씁니다.
PowerShell의 `>`는 파일을 UTF-16으로 저장해 한글이 깨지고 `<`는 지원하지 않으므로, 리다이렉트 대신
컨테이너 안에서 파일을 만들고 `docker cp`로 옮깁니다.

## 1. 시연 전 백업

```bash
docker exec riftdata-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --add-drop-database --databases riftdata --result-file=/tmp/riftdata_before_demo.sql'
mkdir -p sql/demo/backup
docker cp riftdata-mysql:/tmp/riftdata_before_demo.sql sql/demo/backup/riftdata_before_demo.sql
```

- Windows PowerShell에서 `mkdir -p`가 오류를 내면 `New-Item -ItemType Directory -Force sql/demo/backup`을 씁니다.
- `--add-drop-database`가 들어 있어 복원할 때 DB를 지우고 다시 만듭니다. 시연 중 추가된 행이 남지 않습니다.
- `sql/demo/backup/`은 `.gitignore`에 있어 커밋되지 않습니다.
- `mysqldump: [Warning] Using a password on the command line...` 경고는 정상입니다.

## 2. 시연 후 복원

```bash
docker cp sql/demo/backup/riftdata_before_demo.sql riftdata-mysql:/tmp/riftdata_before_demo.sql
docker exec riftdata-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/riftdata_before_demo.sql'
```

복원 확인 (테이블별 행 수를 시연 전과 비교):

```bash
docker exec riftdata-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" riftdata -e "SELECT (SELECT COUNT(*) FROM team) team, (SELECT COUNT(*) FROM lck_match) lck_match, (SELECT COUNT(*) FROM match_set) match_set, (SELECT COUNT(*) FROM team_set) team_set, (SELECT COUNT(*) FROM player_set) player_set, (SELECT COUNT(*) FROM ban) ban, (SELECT COUNT(*) FROM player_season) player_season"'
```

## 3. 참고

- **계정은 복원되지 않습니다.** `riftdata_app`, `riftdata_loader`는 `mysql` 시스템 DB에 있어 백업 범위 밖입니다.
  다음 시연에도 그대로 쓰면 되고, 지우려면 `01_users_grants.sql` 맨 아래의 `DROP USER`를 실행합니다.
- 백업 없이 시연 데이터만 지우려면 `03_exceptions.sql`, `04_state_change.sql`의 마지막 "정리" 부분을 실행합니다.
  두 스크립트 모두 시연용 행(팀코드 `EXA`/`EXB`, `DMA`/`DMB`)만 넣고 지웁니다.
