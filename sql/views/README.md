# 화면별 조회 뷰

화면마다 필요한 데이터를 SQL 뷰 하나로 조회합니다. 발표 시연, 서버 계산 검증, 즉석 질문 대응용입니다.
만드는 스크립트는 [screen_views.sql](screen_views.sql) 하나이고, 다시 실행해도 됩니다 (`CREATE OR REPLACE`).

## 공통 주의사항

- **서버(API)는 뷰를 쓰지 않습니다.** 서버는 지금처럼 Java로 계산하고, 뷰는 같은 규칙을 SQL로 옮긴 것입니다.
  `schema.sql`·엔티티는 바꾸지 않았고, `ddl-auto: validate`는 엔티티 테이블만 검사하므로 뷰가 있어도 서버와 테스트에 영향이 없습니다.
- **일반 뷰라 결과를 저장하지 않습니다.** 조회할 때마다 안의 SELECT를 실행하므로 테이블을 바꾸면 뷰 결과도 바로 바뀝니다.
- **평점** = 6.0 + (total_points − 50) ÷ 12.5, 0~10으로 자르고 소수 1자리 (서버 `Rating`).
  평균 평점은 세트별로 반올림한 평점의 평균을 다시 소수 1자리로 반올림합니다 (서버 `Rating.average`).
- **반올림**은 서버(`Decimals`)와 같은 HALF_UP이고 자릿수도 같습니다: 비율 3자리, KDA·분당 지표 2자리, 평점 1자리.
  MySQL은 나눗셈을 소수 4자리에서 먼저 반올림하므로, 뷰는 소수 10자리로 나눈 뒤 반올림합니다.
- **순위표에 승자승 비교가 없습니다.** 뷰는 경기 승수 → 세트 득실까지만 보고 같으면 같은 순위(`RANK()`)입니다.
  서버는 그다음 승자승으로 동률을 나누므로 동률 팀의 순위가 다를 수 있습니다 (아래 "서버와 다른 점").
- **등급(A+~C)은 뷰에 없습니다.** DB에 등급 컬럼이 없고 서버 코드(`PlayerGrade`)가 점수를 등급으로 바꿉니다. 뷰에는 점수만 있습니다.
- **경기 상태**: 세트 승리 기록이 하나라도 있으면 `FINISHED`, 없으면 `SCHEDULED` (서버 `MatchStatus`).
  순위표는 한 팀이 과반 세트(Bo3 2, Bo5 3)를 따낸 경기만 셉니다 (`v_match_score.is_decided`).
- 데이터가 2026 시즌뿐이라 서버의 시즌 기간 조건(2026-01-01 ~ 2027-01-01)은 넣지 않았습니다.
- 조회는 SELECT 권한만 있으면 됩니다 (`riftdata_app`, `riftdata_loader`). 만들 때는 CREATE VIEW 권한이 필요해 root로 실행합니다.

## 만들기·지우기

```bash
# 로컬 (docker compose의 riftdata-mysql)
docker exec -i riftdata-mysql mysql -uroot -p riftdata < sql/views/screen_views.sql

# 배포 서버 (VM의 ~/lol-db-backend에서)
docker compose -f docker-compose.prod.yml --env-file prod.env exec -T mysql \
  mysql -uroot -p"$(grep ^DB_ROOT_PASSWORD prod.env | cut -d= -f2)" riftdata < sql/views/screen_views.sql
```

지우는 `DROP VIEW` 문은 `screen_views.sql` 맨 아래 주석에 있습니다. `mysqldump --databases riftdata` 백업에는 뷰도 함께 들어갑니다.

## 뷰 목록

| 뷰 | 화면 | 대응 API | 한 행 | 주요 컬럼 | 예시 쿼리 |
| --- | --- | --- | --- | --- | --- |
| `v_match_score` | (공통) | 경기 스코어 계산 | 경기 1개 | `team1_score`, `team2_score`, `won_sets`, `is_decided` | `SELECT * FROM v_match_score WHERE match_id = 19417;` |
| `v_match_list` | SCR-001 홈, SCR-002 일정·결과 | `GET /api/v1/matches` | 경기 1개 | `match_date`, `stage_name`, `status`, `team1_code`·`team1_score`, `team2_code`·`team2_score`, `winner_code` | `SELECT * FROM v_match_list WHERE match_date BETWEEN '2026-04-20' AND '2026-04-26' ORDER BY match_date DESC, match_time;` |
| `v_set_pog` | SCR-001 홈, SCR-003 경기 상세 | `GET /api/v1/matches/{id}`의 `pogs` | 세트 1개 | `set_number`, `nickname`, `team_code`, `champion`, `rating` | `SELECT * FROM v_set_pog WHERE match_id = 19417 ORDER BY set_number;` |
| `v_match_player_rating` | SCR-003 경기 상세 | `GET /api/v1/matches/{id}`의 `playerRatings` | 선수 1명 × 세트 1개 | `set_number`, `team_code`, `position`, `nickname`, `total_points`, `rating`, `is_pog` | `SELECT * FROM v_match_player_rating WHERE match_id = 19417 ORDER BY team_code, position, set_number;` |
| `v_set_team` | SCR-004 세트 상세 | `GET /api/v1/matches/{id}/sets/{n}`의 `teams[].objectives` | 세트 1개 × 팀 1개 | `side`, `victory`, `kills`, `gold`, `towers`, `dragons`, `void_grubs`, `barons`, `inhibitors`, `gold_at_15` | `SELECT * FROM v_set_team WHERE match_id = 19417 AND set_number = 3 ORDER BY side;` |
| `v_set_scoreboard` | SCR-004 세트 상세 | `GET /api/v1/matches/{id}/sets/{n}`의 `teams[].players` | 세트 1개 × 선수 1명 | `kills`·`deaths`·`assists`, `kda`, `cs`, `gold`, `dpm`, `damage_share`, `vision_points`, `rating`, `is_pog` | `SELECT * FROM v_set_scoreboard WHERE match_id = 19417 AND set_number = 3 ORDER BY side, position;` |
| `v_set_ban` | SCR-004 세트 상세 | `GET /api/v1/matches/{id}/sets/{n}`의 `teams[].bans` | 밴 1개 | `side`, `team_code`, `ban_order`, `champion` | `SELECT * FROM v_set_ban WHERE match_id = 19417 AND set_number = 3 ORDER BY side, ban_order;` |
| `v_standings` | SCR-001 홈, SCR-005 순위 | `GET /api/v1/standings` | 팀 1개 | `matches`, `wins`, `losses`, `set_wins`, `set_losses`, `set_diff`, `win_rate`, `standing_rank` | `SELECT * FROM v_standings ORDER BY standing_rank, team_code;` |
| `v_team_roster` | SCR-006 팀 상세 | `GET /api/v1/teams/{id}`의 `roster` | 선수 1명 | `position`, `nickname`, `sets`, `avg_rating`, `kda`, `season_points`, `rank_in_position` | `SELECT * FROM v_team_roster WHERE team_code = 'T1' ORDER BY FIELD(position,'TOP','JUNGLE','MID','ADC','SUPPORT'), nickname;` |
| `v_player_ranking` | SCR-007 선수 랭킹 | `GET /api/v1/players` | 선수 1명 | `sets`, `low_sample`, `avg_rating`, `season_points`, 영역 점수 6개, `position_rank`, `overall_rank` | `SELECT * FROM v_player_ranking WHERE position = 'MID' AND low_sample = 0 ORDER BY position_rank IS NULL, position_rank, player_id;` |
| `v_player_games` | SCR-013 선수 경기 기록 | `GET /api/v1/players/{id}/games` | 선수 1명 × 세트 1개 | `opponent_code`, `victory`, `champion`, `kda`, `cs_per_min`, `dpm`, `gold_diff_15`·`cs_diff_15`·`xp_diff_15`, `rating` | `SELECT * FROM v_player_games WHERE nickname = 'Faker' ORDER BY match_time DESC, match_id DESC, set_number;` |
| `v_player_summary` | SCR-008 선수 종합 | `GET /api/v1/players/{id}/overview` (참고용 요약) | 선수 1명 | `sets`, `wins`, `win_rate`, `kda`, `cs_per_min`, `dpm`, `gold_diff_15`, `avg_rating`, `best_rating`, `pog_count` | `SELECT * FROM v_player_summary WHERE nickname = 'Faker';` |
| `v_champion_stats` | SCR-016 챔피언 통계 | `GET /api/v1/champions` | 챔피언 1개 | `picks`, `wins`, `win_rate`, `kda`, `avg_rating`, `bans`, `pick_ban_rate` | `SELECT * FROM v_champion_stats ORDER BY pick_ban_rate DESC, picks DESC, champion LIMIT 10;` |

- `v_player_ranking`의 `position_rank`는 `?position=X`로 거른 응답의 `rank`, `overall_rank`는 포지션을 거르지 않은 응답의 `rank`와 같습니다.
  10세트 미만(`low_sample = 1`)이면 점수와 순위가 비고, 저장된 시즌 순위는 `stored_rank_in_position`에 그대로 둡니다.
- `v_player_summary`는 화면의 시즌 요약 숫자를 SQL로 보여 주는 용도입니다. 서버의 선수 종합 API는 포지션 평균·상위% 등을 함께 계산하므로 응답 모양이 다르고, 직접 비교하지는 않았습니다.

## 서버와 다른 점

2026-11-24에 로컬 DB(2026 LCK, 경기 148·세트 377·선수 65)로 API 응답과 뷰를 값 단위로 비교했습니다. 다른 것은 아래 하나입니다.

| 항목 | 서버 | 뷰 | 이유 |
| --- | --- | --- | --- |
| 순위표 동률 순위 | NS 7위, BFX 8위 (10승, 세트 득실 −11로 같고 승자승으로 나눔) | NS·BFX 모두 7위 | 승자승은 동률 팀끼리의 맞대결만 다시 세는 계산이라 한 줄의 SQL 정렬 키로 표현하기 어려워 넣지 않았습니다. 동률 팀이 없으면 같습니다. |
