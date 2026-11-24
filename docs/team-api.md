# 팀·순위 API (API-01, 02, 08~11)

API 명세서(2026-11-05)의 팀·순위 도메인 API 구현 기준입니다. 명세와 같은 부분은 명세서를 보고, 이 문서는 **명세와 다른 부분**과 **실제 응답 예시**만 적습니다.
실행 중인 서버에서는 `/swagger-ui.html`에서 모든 필드를 볼 수 있습니다.

## 명세와 다른 부분

| API | 명세 | 구현 | 이유 |
| --- | --- | --- | --- |
| 공통 | `Position`: `TOP, JGL, MID, BOT, SUP` | `TOP, JUNGLE, MID, ADC, SUPPORT` | 최종 물리 DB 설계의 ENUM 값 (경기 API와 같음) |
| 공통 | 400 코드 `INVALID_PARAMETER`, 404 `TEAM_NOT_FOUND` | `COMMON_001`, `TEAM_001` | 공통 예외 처리(`ErrorCode`)의 코드 체계 |
| 공통 | 팀 객체 `{teamId, code, name}` | `{teamId, code}`. `name` 필드 없음 | `team` 테이블에 팀명 컬럼이 없음. 경기·선수 API의 팀 객체와 같은 모양 |
| 01 `/meta` | `rounds: [{code, name}]`, `season.currentRound` | `stages: [{stageId, name, regular}]`, `season.currentStage` | DB에 라운드(R1/R2/PO)가 없고 `stage`만 있음. 경기 목록의 `stageId` 필터와 같은 값 |
| 01 `/meta` | `lastUpdatedAt` = 수집이력.완료일시 최댓값 | 선수시즌지표.계산일시 최댓값 | 수집이력 테이블이 최종 DB에 없음. 적재 → 지표 재계산이 마지막 단계라 대신 씀 |
| 01 `/meta` | `season.*`, `playoffTeamCount`, `minSetsForGrade` 설정값 | 같음. 기본값은 코드(`SeasonSettings`)에 있고 `app.season.*`로 바꿀 수 있음 | `application.yml`을 건드리지 않으려고 |
| 02 `/search` | 팀은 팀명·약어로 검색 | 팀코드로만 검색, `teams[]`는 `{teamId, code}` | `team` 테이블에 팀명 컬럼이 없음 |
| 02 `/search` | `LIKE 'q%'` | 앞부분 일치를 먼저, 그다음 중간 일치. `%`·`_`는 글자 그대로 | 명세의 "앞부분 일치 우선" 문구를 따름 |
| 08 `/standings` | `round` 파라미터 | 없음. 정규 시즌(`stage.is_regular`) 전체만 | 라운드 컬럼이 없음 |
| 08 `/standings` | `rows[].name` | 필드 없음 | 팀명 컬럼 없음 |
| 08 `/standings` | `asOf` | 집계에 들어간 마지막 경기 날짜, 경기가 없으면 null | 명세에 정의가 없어 정함 |
| 10 `/teams` | `name` | 필드 없음 | 팀명 컬럼 없음 |
| 11 `/teams/{id}` | `name`, `coach` | 필드 없음 | 팀명·감독 컬럼 없음 |
| 11 `/teams/{id}` | `roster[]` 필드 | `realName`(실명), `sets`(평균 계산에 쓴 세트 수) 추가 | 화면에서 표본 크기를 보여 줄 수 있게 |
| 11 `/teams/{id}` | `teamStats[].leagueRank` | 골드 차·첫 드래곤·첫 포탑·첫 바론만 순위, 경기 시간·블루/레드 승률은 null | 명세 예시에서 블루 승률 순위가 null이고, 경기 시간은 길고 짧음이 좋고 나쁨이 아님 |
| 11 `/teams/{id}` | `teamStats[].key` | `GD15, FIRST_DRAGON_RATE, FIRST_TOWER_RATE, FIRST_BARON_RATE, AVG_DURATION_SEC, BLUE_WIN_RATE, RED_WIN_RATE` | 명세는 예시 3개만 있어서 화면설계서 SCR-006 ③ 항목으로 채움 |
| 09 `/standings/leaders` | 지표 리더 | 같음. 기록이 있는 모든 세트(플레이오프 포함)로 계산 | 명세에 범위가 없어 정함 |

## 계산 규칙

- 순위: 경기 승수 → 세트 득실 → 승자승(같은 팀들끼리 치른 정규 시즌 경기의 승수). **승자승까지 같으면 같은 순위**이고, 화면 순서는 팀코드순입니다.
- 끝난 경기: 한 팀이 승리 세트 수(Bo3 2, Bo5 3)에 도달한 경기만 셉니다.
- 공통 소수 자릿수(`global/common/Decimals`, HALF_UP): 비율(0~1) 3자리, KDA·분당 지표·골드·CS 차 평균 2자리, 점수(0~100)·평점 1자리.
  - 비율(승률·첫 오브젝트 획득률): 0~1, 소수 3자리.
  - KDA: (K + A) ÷ max(D, 1), 소수 2자리. DPM·VSPM·15분 골드 차(팀·지표 리더): 소수 2자리.
  - 세트 평점: 명세 1.5 식, 소수 1자리. 평균 경기 시간은 초 단위 정수.
- 15분 골드 차(팀): 양 팀 5명 모두 15분 골드가 있는 세트만 평균합니다.
- 15분 골드 차(지표 리더): 같은 세트, 같은 포지션의 상대 팀 선수와 비교하고, 둘 다 값이 있는 세트만 평균합니다.
- DPM·VSPM(지표 리더): 선수 API와 같이 세트마다 분당 값을 구해 평균합니다 (경기 시간 0인 세트 제외). 전체 합계 ÷ 전체 시간이 아닙니다.
- 지표 리더 동점: 세트 수가 많은 선수, 그다음 닉네임순입니다.

## 응답 예시

값은 예시입니다.

`GET /api/v1/standings`

```json
{
  "asOf": "2026-04-26",
  "playoffTeamCount": 6,
  "rows": [
    { "rank": 1, "teamId": 1, "code": "AUR", "matches": 8, "wins": 6, "losses": 2,
      "setWins": 13, "setLosses": 7, "setDiff": 6, "winRate": 0.75, "recent5": ["W", "W", "L", "W", "L"] }
  ]
}
```

`GET /api/v1/teams/1`

```json
{
  "teamId": 1, "code": "AUR",
  "rank": 1, "wins": 6, "losses": 2, "setWins": 13, "setLosses": 7,
  "roster": [
    { "playerId": 13, "name": "ASTER", "realName": null, "position": "MID", "grade": "A-",
      "sets": 20, "avgRating": 7.3, "kda": 4.83, "mainChampion": { "championId": 136, "name": "Azir" } }
  ],
  "teamStats": [
    { "key": "GD15", "label": "15분 골드 차", "value": 1240.56, "leagueRank": 1 },
    { "key": "FIRST_DRAGON_RATE", "label": "첫 드래곤 획득률", "value": 0.65, "leagueRank": 2 },
    { "key": "FIRST_TOWER_RATE", "label": "첫 포탑 획득률", "value": 0.6, "leagueRank": 3 },
    { "key": "FIRST_BARON_RATE", "label": "첫 바론 획득률", "value": 0.7, "leagueRank": 1 },
    { "key": "AVG_DURATION_SEC", "label": "평균 경기 시간", "value": 1925, "leagueRank": null },
    { "key": "BLUE_WIN_RATE", "label": "블루 승률", "value": 0.7, "leagueRank": null },
    { "key": "RED_WIN_RATE", "label": "레드 승률", "value": 0.6, "leagueRank": null }
  ],
  "recentMatches": [
    { "matchId": 41, "date": "2026-04-26", "opponent": { "teamId": 2, "code": "TTN" }, "win": true, "score": "2:1" }
  ]
}
```

`GET /api/v1/meta`

```json
{
  "season": { "name": "2026 LCK", "startDate": "2026-04-01", "endDate": "2026-06-14", "teamCount": 10,
              "playoffTeamCount": 6, "currentStage": { "stageId": 1, "name": "Regular Season", "regular": true } },
  "patches": ["26.6", "26.7", "26.8"],
  "stages": [ { "stageId": 1, "name": "Regular Season", "regular": true }, { "stageId": 2, "name": "Playoffs", "regular": false } ],
  "minSetsForGrade": 10,
  "lastUpdatedAt": "2026-04-26T23:10:00+09:00",
  "dataSource": "Oracle's Elixir (2026 LCK)"
}
```

`GET /api/v1/search?q=as&limit=5`

```json
{
  "players": [ { "playerId": 13, "name": "ASTER", "position": "MID", "teamId": 1, "teamCode": "AUR" } ],
  "teams": [],
  "champions": [ { "championId": 40, "name": "Ashe" } ]
}
```

`GET /api/v1/standings/leaders`

```json
[
  { "metric": "KDA", "label": "KDA", "playerId": 21, "name": "Vale", "teamCode": "AUR", "value": 6.13 },
  { "metric": "DPM", "label": "분당 피해량", "playerId": 21, "name": "Vale", "teamCode": "AUR", "value": 712.35 },
  { "metric": "GD15", "label": "15분 골드 차", "playerId": 14, "name": "Kestrel", "teamCode": "TTN", "value": 820.5 },
  { "metric": "VSPM", "label": "분당 시야 점수", "playerId": 25, "name": "Halo", "teamCode": "AUR", "value": 2.85 },
  { "metric": "AVG_RATING", "label": "평균 평점", "playerId": 21, "name": "Vale", "teamCode": "AUR", "value": 7.6 }
]
```
