# 발표 시연용 SQL

시연 중 **권한 검증**, **예외 상황(제약 조건 위반)**, **데이터 상태 변경**을 바로 보여 주는 스크립트입니다.
서비스에 로그인이 없어서 권한 검증은 DB 계정 권한 분리로 보여 줍니다.

| 파일 | 보여 주는 것 | 실행 계정 |
| --- | --- | --- |
| [00_backup_restore.md](00_backup_restore.md) | 시연 전 백업, 시연 후 복원 | root |
| [01_users_grants.sql](01_users_grants.sql) | 조회 전용 앱 계정 / 적재 계정 만들기 | root |
| [02_privilege_check.sql](02_privilege_check.sql) | 앱 계정은 SELECT만 되고 변경은 ERROR 1142 | riftdata_app |
| [03_exceptions.sql](03_exceptions.sql) | CHECK·UNIQUE·FK 위반을 DB가 거부 (전부 실패해야 정상) | riftdata_loader |
| [04_state_change.sql](04_state_change.sql) | INSERT·UPDATE·DELETE가 API에 바로 반영, CASCADE 삭제 | riftdata_loader |
| [../views/screen_views.sql](../views/screen_views.sql) | 화면별 조회 뷰 13개 (만들기는 root, 조회는 SELECT 권한만). 목록과 설명은 [views/README.md](../views/README.md) | root |

## 시연 순서

명령은 프로젝트 루트에서 실행합니다. MySQL은 `docker compose up -d`로 켠 `riftdata-mysql` 컨테이너 기준입니다.

1. **백업**: [00_backup_restore.md](00_backup_restore.md)의 "1. 시연 전 백업"
2. **계정 만들기 (01)**: root로 접속해 실행합니다. 처음 한 번만 하면 됩니다. 비밀번호 예시값은 바꿔서 씁니다.
   ```bash
   docker exec -it riftdata-mysql mysql -uroot -p
   ```
3. **서버를 조회 전용 계정으로 실행**: `.env`에 아래를 추가하고 `./gradlew bootRun` (Windows는 `.\gradlew.bat bootRun`)
   ```properties
   DB_APP_USERNAME=riftdata_app
   DB_APP_PASSWORD=01에서 정한 비밀번호
   ```
   서버가 SELECT 권한만으로 정상 기동하고 모든 API(전부 GET)가 동작합니다. 테스트(`./gradlew test`)는 이 값을 쓰지 않고 기존 `DB_USERNAME` 계정을 씁니다.
4. **권한 검증 (02)**: `riftdata_app`으로 접속해 한 줄씩 실행 → SELECT 성공, INSERT·UPDATE·DELETE·DROP은 ERROR 1142
   ```bash
   docker exec -it riftdata-mysql mysql -uriftdata_app -p riftdata
   ```
5. **예외 상황 (03)**: `riftdata_loader`로 접속해 블록 단위로 실행 → [준비] 성공, 1~11 모두 오류, [정리]
   ```bash
   docker exec -it riftdata-mysql mysql -uriftdata_loader -p riftdata
   ```
6. **상태 변경 (04)**: 같은 방식으로 `riftdata_loader` 접속. 단계마다 주석의 API 주소를 브라우저나 Swagger UI(`http://localhost:8080/swagger-ui.html`)에서 열어 바뀐 값을 보여 줍니다.
   세션 변수를 쓰므로 [준비]부터 **같은 접속에서** 순서대로 실행합니다.
7. **복원**: [00_backup_restore.md](00_backup_restore.md)의 "2. 시연 후 복원" (또는 03·04 맨 아래 [정리])

## 파일을 한 번에 실행할 때

mysql 클라이언트는 첫 오류에서 멈추므로 02·03은 `--force`를 붙입니다. `-v`를 붙이면 실행한 쿼리도 함께 출력됩니다.
PowerShell은 `<` 리다이렉트를 지원하지 않으므로 파일을 컨테이너에 복사한 뒤 실행합니다 (맥에서도 같은 명령 사용 가능).

```bash
docker cp sql/demo/03_exceptions.sql riftdata-mysql:/tmp/03_exceptions.sql
docker exec -it riftdata-mysql sh -c "mysql --force -v -uriftdata_loader -p riftdata < /tmp/03_exceptions.sql"
```

## 알아 둘 점

- **04(b)의 재계산은 결과 컬럼을 직접 UPDATE합니다.** 세트 점수(B3)와 선수 시즌 지표(B4)는 적재 프로그램([docs/csv-loader.md](../../docs/csv-loader.md))이
  계산하지만, 시즌 전체 분포로 다시 매기므로 시연 값이 실데이터에 따라 달라집니다. 그래서 04(b)는 재계산 결과 컬럼(`player_set.total_points`, `player_season`)을 직접 UPDATE해서
  재계산이 끝난 상태를 만듭니다.
- **세트 수(`lck_match.sets`)에는 CHECK가 없습니다.** 3·5가 아닌 값도 들어갑니다. 03 맨 아래 "참고"에 적어 두었고,
  막으려면 `schema.sql`에 `CHECK (sets IN (3, 5))`를 추가해야 합니다.
- **FK 오류 코드는 계정마다 다릅니다.** `riftdata_loader`로 실행하면 1216/1217(부모 테이블 정보를 숨긴 일반 메시지)이,
  root로 실행하면 1452/1451(FK 이름·부모 테이블 표시)이 나옵니다. MySQL은 부모 테이블에 테이블 단위 권한이 없는 계정에
  자세한 FK 정보를 보여 주지 않습니다. 둘 다 거부되는 것은 같고, 권한 분리의 효과로 설명할 수 있습니다.
- **뷰는 없습니다.** `schema.sql`에 테이블 10개만 있습니다 (순위표 등은 서버가 조회 때 계산).
- 03·04는 시연용 행(팀코드 `EXA`/`EXB`, `DMA`/`DMB`)만 넣고 지우므로 실데이터가 적재된 DB에서도 그대로 쓸 수 있습니다.
  실데이터에 같은 팀코드·닉네임·챔피언 이름(`DemoChamp` 등)이 있으면 충돌하므로 시연 전에 확인합니다.
