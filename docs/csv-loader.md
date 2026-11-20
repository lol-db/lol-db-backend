# CSV 적재와 점수 재계산

Oracle's Elixir 2026 경기 CSV를 DB에 넣고, 세트 점수·POG(B3)와 선수 시즌 지표(B4)를 계산하는 프로그램입니다.
서버와 같은 프로젝트의 `loader` 프로필로 실행하며, 웹 서버 없이 적재만 하고 끝납니다.

- 코드: `src/main/java/com/riftdata/batch`
- 기준: 화면설계서 v0.5 "등급·세트 평점 기준"·"지표 사전", 인계서 BR-001~BR-016, BR-159~BR-164
- 점수 계산은 API와 같은 지표 정의(`PlayerMetric`)를 그대로 써서, API가 보여 주는 지표와 저장된 점수가 어긋나지 않습니다.

## 1. 준비

1. [Oracle's Elixir](https://oracleselixir.com/tools/downloads)에서 `2026_LoL_esports_match_data_from_OraclesElixir.csv`를 받습니다.
2. MySQL을 켜고 `schema.sql`로 테이블을 만들어 둡니다 ([README](../README.md)).
3. 팀코드 매핑을 확인합니다. CSV의 `teamname`을 `team.code`(최대 4자)로 바꾸는 표가
   `src/main/resources/application-loader.yml`의 `loader.team-codes`에 있습니다. 매핑이 없는 팀이 있으면 적재 전에 오류로 알려 줍니다.

## 2. 실행

프로젝트 루트에서 실행합니다. Windows PowerShell은 `./gradlew` 대신 `.\gradlew.bat`입니다.

```bash
# 적재 (이미 넣은 gameid는 건너뜀 → 시즌 중 새 CSV를 다시 넣어도 됨)
./gradlew bootRun --args="--spring.profiles.active=loader --loader.file=/경로/2026_LoL_esports_match_data_from_OraclesElixir.csv"

# 경기 데이터를 모두 지우고 다시 적재 (팀·선수·챔피언은 남김)
./gradlew bootRun --args="--spring.profiles.active=loader --loader.file=/경로/파일.csv --loader.replace=true"

# CSV 없이 재계산만 (기록을 직접 고친 뒤 등)
./gradlew bootRun --args="--spring.profiles.active=loader --loader.recalculate-only=true"
```

- 실패하면 종료 코드 1로 끝나고, 아무것도 바뀌지 않습니다.
- DB 계정은 `DB_LOADER_USERNAME`, `DB_LOADER_PASSWORD`(예: `sql/demo/01`의 `riftdata_loader`)를 쓰고, 없으면 `DB_USERNAME`, `DB_PASSWORD`를 씁니다.
- 끝나면 적재 결과(새 경기·세트 수, 제외한 행, 세트 점수·POG·시즌 지표 수)를 출력합니다.

## 3. 적재 규칙

| 항목 | 규칙 |
| --- | --- |
| 대상 | `league = LCK`, `datacompleteness = complete`, 한국 시간 기준 2026년 경기 (BR-001, BR-002) |
| 시간 | 원천 `date`는 UTC라 한국 시간(+9)으로 바꿔 저장 (`loader.source-zone`) |
| 사전 검증 | 경기마다 선수 10행(양 팀 TOP~SUPPORT 한 명씩) + 팀 2행, 승리 팀 1개, 세트 번호 1~5, 경기 시간, 이름 길이, 팀코드 매핑. **오류가 하나라도 있으면 적재하지 않고** 줄 번호와 함께 모두 출력 (BR-163) |
| 트랜잭션 | 적재와 재계산을 한 트랜잭션으로 처리하고, 실패하면 전체 롤백 (BR-164) |
| 팀·챔피언 | 팀코드·이름으로 찾고 없으면 추가. 밴만 된 챔피언도 추가 |
| 선수 | 닉네임(`playername`)으로 찾음. 소속 팀은 마지막 경기의 팀, 주포지션은 가장 많이 뛴 포지션으로 추가·갱신 |
| 스테이지 | `split`·`playoffs`로 구분. 정규 시즌은 split 이름, 플레이오프는 "… 플레이오프". 기간은 경기 날짜로, 진행순서는 시작일 순 |
| 경기(시리즈) | 원천에 시리즈 정보가 없어 **같은 날(한국 시간) 같은 두 팀의 세트를 한 경기로** 묶음. 팀1 = 첫 세트의 블루 팀 |
| Bo3·Bo5 | 한 팀이라도 3승이면 Bo5, 아니면 Bo3 |
| 세트 | `gameid` → 원본게임ID, `game` → 세트 번호, `patch` → 패치, `gamelength` → 경기 시간 |
| 팀세트 | 진영·승패·선취점·첫 용/전령/바론/포탑/미드 포탑, 용(원소별)·공허 유충·바론·포탑·포탑 방패·억제기 수 |
| 선수세트 | K/D/A, 멀티킬, 선취점 킬·어시·희생, 피해량, 시야·와드, 골드, CS, 10·15·20·25분 지표. 원천이 빈 시간대 지표는 NULL (BR-005) |
| 밴 | 팀 행의 `ban1~ban5` → 밴 순서 1~5 |

## 4. 재계산 규칙

### B3 세트 점수와 POG

1. 세트마다 지표 20개(`GET /api/v1/metrics`)를 그 세트 한 판으로 계산합니다.
2. 시즌 전체에서 **그 세트에 뛴 포지션이 같은 세트들** 안에서 지표별 백분위(0~100)로 바꿉니다. 데스처럼 낮을수록 좋은 지표는 순서를 뒤집습니다.
3. 영역 점수 = 영역 지표 백분위의 평균, 세트 점수 = 영역 점수의 포지션별 가중 평균 (가중치는 API-14와 같은 표).
4. `player_set.total_points`에 반올림해 저장합니다. 세트 평점(6.0 + (점수 − 50) ÷ 12.5)은 API가 조회 때 바꿉니다 (BR-015).
5. POG: 세트마다 승리 팀에서 반올림 전 세트 점수가 가장 높은 선수. 같으면 킬 관여율 높은 순 → 데스 적은 순 → 선수 ID 순 (BR-016).

### B4 선수 시즌 지표

1. 선수별로 시즌 전체 세트로 지표 20개를 계산합니다 (API-14 핵심 지표와 같은 값).
2. **주포지션이 같고 시즌 10세트 이상**인 선수끼리 지표별 백분위를 구합니다 (BR-009, BR-011).
3. 영역 점수 = 영역 지표 백분위 평균, 종합 점수 = 포지션 가중 평균 (BR-012, BR-013). 포지션 내 순위는 종합 점수 순(같으면 같은 순위).
4. `player_season`을 모두 지우고 다시 넣습니다. 10세트 미만 선수는 세트 수만 넣고 점수는 NULL (BR-010).
   등급(A+~C)은 API가 종합 점수로 판정합니다 (BR-014).

백분위 계산식은 API의 상위 %와 같습니다: 백분위 = 100 × (더 나쁜 값 수 + (같은 값 수 − 1) ÷ 2) ÷ (전체 수 − 1).

## 5. 확인

```bash
./gradlew test --tests 'com.riftdata.batch.*'
```

`LckCsvLoaderTests`는 Oracle's Elixir 형식으로 만든 CSV로 적재·시리즈 묶기·Bo3/Bo5·시간 변환·제외 규칙·재적재·사전 검증 실패 시 무변경·점수·POG·시즌 순위와
적재 직후 선수 랭킹 API 응답까지 확인합니다.
