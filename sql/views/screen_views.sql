-- =====================================================================
-- RIFT DATA 화면별 조회 뷰
--
-- 보여 주는 것: 화면마다 필요한 데이터를 SQL 뷰 하나로 조회한다.
--   서버(API)는 이 뷰를 쓰지 않고 Java로 계산한다. 뷰는 발표 시연·검증·즉석 질문 대응용이다.
-- 실행 계정: root (CREATE VIEW 권한 필요). 조회는 SELECT 권한만 있으면 된다 (riftdata_app, riftdata_loader).
-- 접속 명령
--   로컬: docker exec -i riftdata-mysql mysql -uroot -p riftdata < sql/views/screen_views.sql
--   배포: docker compose -f docker-compose.prod.yml --env-file prod.env exec -T mysql \
--           mysql -uroot -p"$(grep ^DB_ROOT_PASSWORD prod.env | cut -d= -f2)" riftdata < sql/views/screen_views.sql
-- 예상 결과: 오류 없이 끝나고, 마지막 SELECT에 뷰 13개 목록이 나온다.
--
-- 참고
-- - 테이블 구조는 바꾸지 않는다. ddl-auto: validate는 엔티티 테이블만 검사하므로 뷰를 추가해도 서버에 영향이 없다.
-- - 일반 뷰라 결과를 저장하지 않는다. 조회할 때마다 안의 SELECT를 실행한다.
-- - CREATE OR REPLACE라 다시 실행해도 된다.
-- - 반올림은 서버(Decimals, Rating)와 같은 HALF_UP이다. 나눗셈은 자릿수를 넉넉히(소수 10자리) 잡은 뒤 반올림한다.
--   MySQL은 나눗셈 결과를 소수 4자리에서 먼저 반올림해서, 그대로 ROUND하면 두 번 반올림돼 서버와 달라질 수 있다.
-- - 평점 = 6.0 + (total_points - 50) / 12.5, 0~10으로 자르고 소수 1자리 (서버 Rating과 같은 식).
--   평균 평점은 세트별로 반올림한 평점의 평균을 다시 소수 1자리로 반올림한다 (서버 Rating.average).
-- - 순위표는 경기 승수 → 세트 득실로만 매긴다. 서버의 승자승 비교는 뷰에 없다 (동률일 때 순위가 다를 수 있다).
-- - 등급(A+~C)은 DB에 컬럼이 없고 서버 코드가 점수로 바꾼다. 뷰에는 점수만 있다.
-- - 데이터가 2026 시즌뿐이라 서버의 시즌 기간 조건(2026-01-01 ~ 2027-01-01)은 넣지 않았다.
-- =====================================================================

-- 한글(스테이지 이름 등)이 깨지지 않게 접속 문자셋을 맞춘다. 컨테이너 안 mysql 클라이언트의 기본값은 latin1이다.
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 공통: 경기 스코어 (한 행 = 경기 1개, 다른 뷰가 사용)
-- won_sets = 세트 승리 기록 수. 0이면 예정 경기다 (서버 MatchStatus와 같은 판단).
-- is_decided = 한 팀이 과반 세트(Bo3 2, Bo5 3)를 따낸 경기. 순위표는 이 경기만 센다 (서버 TeamService와 같은 판단).
-- 예) SELECT * FROM v_match_score WHERE match_id = 19417;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_match_score AS
SELECT m.match_id, m.team1_id, m.team2_id, m.sets AS best_of,
       COALESCE(SUM(t.victory = 1), 0)                        AS won_sets,
       COALESCE(SUM(t.team_id = m.team1_id AND t.victory), 0) AS team1_score,
       COALESCE(SUM(t.team_id = m.team2_id AND t.victory), 0) AS team2_score,
       (COALESCE(SUM(t.team_id = m.team1_id AND t.victory), 0) >= m.sets DIV 2 + 1
        OR COALESCE(SUM(t.team_id = m.team2_id AND t.victory), 0) >= m.sets DIV 2 + 1) AS is_decided
FROM lck_match m
LEFT JOIN match_set s ON s.match_id = m.match_id
LEFT JOIN team_set  t ON t.set_id   = s.set_id
GROUP BY m.match_id, m.team1_id, m.team2_id, m.sets;

-- ---------------------------------------------------------------------
-- SCR-001 홈 · SCR-002 일정·결과: 경기 목록 (한 행 = 경기 1개)
-- 서버 정렬: 날짜 최근순, 같은 날은 시작 시각순
-- 예) SELECT * FROM v_match_list WHERE match_date BETWEEN '2026-04-20' AND '2026-04-26' ORDER BY match_date DESC, match_time;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_match_list AS
SELECT m.match_id,
       DATE(m.match_time)                                     AS match_date,
       m.match_time,
       g.stage_id, g.name AS stage_name, g.is_regular,
       m.sets                                                 AS best_of,
       IF(sc.won_sets > 0, 'FINISHED', 'SCHEDULED')           AS status,
       t1.team_id AS team1_id, t1.code AS team1_code,
       IF(sc.won_sets > 0, sc.team1_score, NULL)              AS team1_score,
       t2.team_id AS team2_id, t2.code AS team2_code,
       IF(sc.won_sets > 0, sc.team2_score, NULL)              AS team2_score,
       CASE WHEN sc.won_sets = 0 THEN NULL
            WHEN sc.team1_score > sc.team2_score THEN t1.code
            WHEN sc.team2_score > sc.team1_score THEN t2.code END AS winner_code
FROM lck_match m
JOIN v_match_score sc ON sc.match_id = m.match_id
JOIN stage g  ON g.stage_id = m.stage_id
JOIN team t1  ON t1.team_id = m.team1_id
JOIN team t2  ON t2.team_id = m.team2_id;

-- ---------------------------------------------------------------------
-- SCR-001 홈 · SCR-003 경기 상세: 세트별 POG (한 행 = 세트 1개)
-- 예) SELECT * FROM v_set_pog WHERE match_id = 19417 ORDER BY set_number;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_set_pog AS
SELECT m.match_id, DATE(m.match_time) AS match_date, s.set_id, s.set_number,
       ts.team_id, tm.code AS team_code,
       p.player_id, p.nickname, ps.position,
       c.champion_id, c.name AS champion,
       LEAST(10, GREATEST(0, ROUND(6 + (CAST(ps.total_points AS SIGNED) - 50) / 12.5, 1))) AS rating
FROM player_set ps
JOIN team_set ts  ON ts.team_set_id = ps.team_set_id
JOIN match_set s  ON s.set_id = ts.set_id
JOIN lck_match m  ON m.match_id = s.match_id
JOIN player p     ON p.player_id = ps.player_id
JOIN team tm      ON tm.team_id = ts.team_id
JOIN champion c   ON c.champion_id = ps.champion_id
WHERE ps.is_pog = 1;

-- ---------------------------------------------------------------------
-- SCR-003 경기 상세: 선수별 세트 평점 (한 행 = 선수 1명 × 세트 1개)
-- 예) SELECT * FROM v_match_player_rating WHERE match_id = 19417 ORDER BY team_code, position, set_number;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_match_player_rating AS
SELECT s.match_id, s.set_number, TIME_TO_SEC(s.play_time) AS duration_sec,
       ts.team_id, tm.code AS team_code, ts.victory,
       ps.position, p.player_id, p.nickname, c.name AS champion,
       ps.total_points,
       LEAST(10, GREATEST(0, ROUND(6 + (CAST(ps.total_points AS SIGNED) - 50) / 12.5, 1))) AS rating,
       ps.is_pog
FROM player_set ps
JOIN team_set ts ON ts.team_set_id = ps.team_set_id
JOIN match_set s ON s.set_id = ts.set_id
JOIN team tm     ON tm.team_id = ts.team_id
JOIN player p    ON p.player_id = ps.player_id
JOIN champion c  ON c.champion_id = ps.champion_id;

-- ---------------------------------------------------------------------
-- SCR-004 세트 상세: 팀 오브젝트 (한 행 = 세트 1개 × 팀 1개)
-- kills·gold는 선수 5명의 합이다 (서버 objectives와 같은 계산).
-- 예) SELECT * FROM v_set_team WHERE match_id = 19417 AND set_number = 3 ORDER BY side;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_set_team AS
SELECT s.match_id, s.set_number, s.set_id, s.game_id, s.version AS patch,
       TIME_TO_SEC(s.play_time) AS duration_sec,
       ts.team_set_id, ts.team_id, tm.code AS team_code, ts.side, ts.victory,
       SUM(ps.kills)               AS kills,
       SUM(ps.deaths)              AS deaths,
       SUM(ps.assists)             AS assists,
       SUM(ps.total_golds)         AS gold,
       SUM(ps.damage_to_champions) AS damage,
       SUM(ps.gold_at_15)          AS gold_at_15,
       ts.towers, ts.dragons, ts.void_grubs, ts.barons, ts.inhibitors, ts.tower_plates,
       ts.first_blood, ts.first_dragon, ts.first_tower, ts.first_baron
FROM team_set ts
JOIN match_set s ON s.set_id = ts.set_id
JOIN team tm     ON tm.team_id = ts.team_id
LEFT JOIN player_set ps ON ps.team_set_id = ts.team_set_id
GROUP BY s.match_id, s.set_number, s.set_id, s.game_id, s.version, s.play_time,
         ts.team_set_id, ts.team_id, tm.code, ts.side, ts.victory,
         ts.towers, ts.dragons, ts.void_grubs, ts.barons, ts.inhibitors, ts.tower_plates,
         ts.first_blood, ts.first_dragon, ts.first_tower, ts.first_baron;

-- ---------------------------------------------------------------------
-- SCR-004 세트 상세: 스코어보드 (한 행 = 세트 1개 × 선수 1명)
-- dpm·피해 비중·KDA는 저장하지 않고 여기서 계산한다 (서버 SetDetailService와 같은 식·자릿수).
--   dpm = 피해량 × 60 ÷ 경기 초, 소수 2자리 / damage_share = 피해량 ÷ 팀 피해량 합, 소수 3자리
--   kda = (킬 + 어시스트) ÷ max(데스, 1), 소수 2자리
-- 예) SELECT * FROM v_set_scoreboard WHERE match_id = 19417 AND set_number = 3 ORDER BY side, position;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_set_scoreboard AS
SELECT s.match_id, s.set_number, s.set_id,
       ts.team_id, tm.code AS team_code, ts.side, ts.victory,
       ps.position, p.player_id, p.nickname, c.champion_id, c.name AS champion,
       ps.kills, ps.deaths, ps.assists,
       ROUND((ps.kills + ps.assists) * 1.0000000000 / GREATEST(ps.deaths, 1), 2) AS kda,
       ps.minion_kills + ps.monster_kills                                        AS cs,
       ps.total_golds                                                            AS gold,
       ROUND(ps.damage_to_champions * 60.0000000000 / NULLIF(TIME_TO_SEC(s.play_time), 0), 2) AS dpm,
       ROUND(ps.damage_to_champions * 1.0000000000
             / NULLIF(SUM(ps.damage_to_champions) OVER (PARTITION BY ps.team_set_id), 0), 3) AS damage_share,
       ps.vision_points,
       LEAST(10, GREATEST(0, ROUND(6 + (CAST(ps.total_points AS SIGNED) - 50) / 12.5, 1))) AS rating,
       ps.is_pog
FROM player_set ps
JOIN team_set ts ON ts.team_set_id = ps.team_set_id
JOIN match_set s ON s.set_id = ts.set_id
JOIN team tm     ON tm.team_id = ts.team_id
JOIN player p    ON p.player_id = ps.player_id
JOIN champion c  ON c.champion_id = ps.champion_id;

-- ---------------------------------------------------------------------
-- SCR-004 세트 상세: 밴 (한 행 = 밴 1개)
-- 예) SELECT * FROM v_set_ban WHERE match_id = 19417 AND set_number = 3 ORDER BY side, ban_order;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_set_ban AS
SELECT s.match_id, s.set_number, s.set_id,
       ts.team_id, tm.code AS team_code, ts.side,
       b.ban_order, c.champion_id, c.name AS champion
FROM ban b
JOIN team_set ts ON ts.team_set_id = b.team_set_id
JOIN match_set s ON s.set_id = ts.set_id
JOIN team tm     ON tm.team_id = ts.team_id
JOIN champion c  ON c.champion_id = b.champion_id;

-- ---------------------------------------------------------------------
-- SCR-001 홈 · SCR-005 순위: 정규 스테이지(is_regular) 순위표 (한 행 = 팀 1개)
-- 끝난 경기(is_decided)만 센다. 경기가 없으면 승률 0 (서버와 같음).
-- 순위는 승수 → 세트 득실까지만 본다. 서버는 그다음 승자승으로 동률을 나누고, 화면 순서는 팀코드순이다.
-- 예) SELECT * FROM v_standings ORDER BY standing_rank, team_code;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_standings AS
SELECT x.team_id, x.team_code,
       x.matches, x.wins, x.losses, x.set_wins, x.set_losses,
       x.set_wins - x.set_losses                                             AS set_diff,
       COALESCE(ROUND(x.wins * 1.0000000000 / NULLIF(x.matches, 0), 3), 0)  AS win_rate,
       RANK() OVER (ORDER BY x.wins DESC, (x.set_wins - x.set_losses) DESC)  AS standing_rank
FROM (
  SELECT tm.team_id, tm.code AS team_code,
         COUNT(r.match_id)                        AS matches,
         COALESCE(SUM(r.won), 0)                  AS wins,
         COALESCE(SUM(1 - r.won), 0)              AS losses,
         COALESCE(SUM(r.my_score), 0)             AS set_wins,
         COALESCE(SUM(r.op_score), 0)             AS set_losses
  FROM team tm
  LEFT JOIN (
    SELECT sc.match_id, sc.team1_id AS team_id, sc.team1_score AS my_score, sc.team2_score AS op_score,
           (sc.team1_score >= sc.best_of DIV 2 + 1) AS won
    FROM v_match_score sc
    JOIN lck_match m ON m.match_id = sc.match_id
    JOIN stage g     ON g.stage_id = m.stage_id
    WHERE g.is_regular = 1 AND sc.is_decided = 1
    UNION ALL
    SELECT sc.match_id, sc.team2_id, sc.team2_score, sc.team1_score,
           (sc.team2_score >= sc.best_of DIV 2 + 1)
    FROM v_match_score sc
    JOIN lck_match m ON m.match_id = sc.match_id
    JOIN stage g     ON g.stage_id = m.stage_id
    WHERE g.is_regular = 1 AND sc.is_decided = 1
  ) r ON r.team_id = tm.team_id
  GROUP BY tm.team_id, tm.code
) x;

-- ---------------------------------------------------------------------
-- SCR-006 팀 상세: 로스터 (한 행 = 선수 1명)
-- sets·avg_rating·kda는 그 선수의 모든 세트 기록으로 계산한다 (서버 로스터와 같음). season_points는 저장된 시즌 점수.
-- 예) SELECT * FROM v_team_roster WHERE team_code = 'T1' ORDER BY FIELD(position,'TOP','JUNGLE','MID','ADC','SUPPORT'), nickname;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_team_roster AS
SELECT tm.team_id, tm.code AS team_code,
       p.player_id, p.nickname, p.name, p.position,
       COALESCE(st.sets, 0) AS sets,
       st.avg_rating,
       st.kda,
       ss.total_points AS season_points, ss.rank_in_position
FROM player p
JOIN team tm ON tm.team_id = p.team_id
LEFT JOIN player_season ss ON ss.player_id = p.player_id
LEFT JOIN (
  SELECT ps.player_id, COUNT(*) AS sets,
         ROUND(AVG(LEAST(10, GREATEST(0, ROUND(6 + (CAST(ps.total_points AS SIGNED) - 50) / 12.5, 1)))), 1) AS avg_rating,
         ROUND((SUM(ps.kills) + SUM(ps.assists)) * 1.0000000000 / GREATEST(SUM(ps.deaths), 1), 2) AS kda
  FROM player_set ps
  GROUP BY ps.player_id
) st ON st.player_id = p.player_id;

-- ---------------------------------------------------------------------
-- SCR-007 선수 랭킹 (한 행 = 선수 1명)
-- sets = 세트 출전 수 (player_set 행 수). 10 미만이면 low_sample = 1이고 점수·순위를 비운다 (서버와 같음).
-- avg_rating = 세트별 평점(소수 1자리)의 평균. 점수가 없는 세트는 평균에서 뺀다.
-- position_rank = 같은 포지션에서 종합 점수 순위 (/api/v1/players?position=X의 rank)
-- overall_rank  = 전체 선수 중 종합 점수 순위 (/api/v1/players의 rank)
-- 예) SELECT * FROM v_player_ranking WHERE position = 'MID' AND low_sample = 0 ORDER BY position_rank IS NULL, position_rank, player_id;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_player_ranking AS
SELECT y.*,
       CASE WHEN y.low_sample = 0 AND y.season_points IS NOT NULL
            THEN RANK() OVER (PARTITION BY y.position, (y.low_sample = 0 AND y.season_points IS NOT NULL)
                              ORDER BY y.season_points DESC) END AS position_rank,
       CASE WHEN y.low_sample = 0 AND y.season_points IS NOT NULL
            THEN RANK() OVER (PARTITION BY (y.low_sample = 0 AND y.season_points IS NOT NULL)
                              ORDER BY y.season_points DESC) END AS overall_rank
FROM (
  SELECT p.player_id, p.nickname, p.position, tm.team_id, tm.code AS team_code,
         COALESCE(st.sets, 0)                                  AS sets,
         (COALESCE(st.sets, 0) < 10)                           AS low_sample,
         st.avg_rating,
         IF(COALESCE(st.sets, 0) >= 10 AND ss.sets >= 10 AND ss.total_points BETWEEN 0 AND 100,
            ss.total_points, NULL)                             AS season_points,
         IF(COALESCE(st.sets, 0) >= 10 AND ss.sets >= 10, ss.combat_points,   NULL) AS combat_points,
         IF(COALESCE(st.sets, 0) >= 10 AND ss.sets >= 10, ss.damage_points,   NULL) AS damage_points,
         IF(COALESCE(st.sets, 0) >= 10 AND ss.sets >= 10, ss.laning_points,   NULL) AS laning_points,
         IF(COALESCE(st.sets, 0) >= 10 AND ss.sets >= 10, ss.growth_points,   NULL) AS growth_points,
         IF(COALESCE(st.sets, 0) >= 10 AND ss.sets >= 10, ss.ward_points,     NULL) AS ward_points,
         IF(COALESCE(st.sets, 0) >= 10 AND ss.sets >= 10, ss.survival_points, NULL) AS survival_points,
         ss.rank_in_position                                   AS stored_rank_in_position,
         ss.calculated_at
  FROM player p
  JOIN team tm ON tm.team_id = p.team_id
  LEFT JOIN player_season ss ON ss.player_id = p.player_id
  LEFT JOIN (
    SELECT ps.player_id, COUNT(*) AS sets,
           ROUND(AVG(LEAST(10, GREATEST(0, ROUND(6 + (CAST(ps.total_points AS SIGNED) - 50) / 12.5, 1)))), 1) AS avg_rating
    FROM player_set ps
    GROUP BY ps.player_id
  ) st ON st.player_id = p.player_id
) y;

-- ---------------------------------------------------------------------
-- SCR-013 선수 경기 기록 (한 행 = 선수 1명 × 세트 1개, 라인 상대와의 15분 차이 포함)
-- cs_per_min·dpm은 소수 2자리, gold_diff_15 = 내 15분 골드 − 상대 팀 같은 포지션의 15분 골드 (서버와 같음).
-- 서버 정렬: 경기 시각 최근순, 같은 경기는 세트 번호순
-- 예) SELECT * FROM v_player_games WHERE nickname = 'Faker' ORDER BY match_time DESC, match_id DESC, set_number;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_player_games AS
SELECT p.player_id, p.nickname, ps.position,
       m.match_id, m.match_time, g.name AS stage_name, g.is_regular, s.set_number, s.version AS patch,
       ts.team_id, tm.code AS team_code, op_tm.team_id AS opponent_id, op_tm.code AS opponent_code,
       ts.victory, c.champion_id, c.name AS champion,
       ps.kills, ps.deaths, ps.assists,
       ps.minion_kills + ps.monster_kills AS cs,
       ps.damage_to_champions             AS damage,
       ROUND((ps.kills + ps.assists) * 1.0000000000 / GREATEST(ps.deaths, 1), 2) AS kda,
       ROUND((ps.minion_kills + ps.monster_kills) * 60.0000000000 / NULLIF(TIME_TO_SEC(s.play_time), 0), 2) AS cs_per_min,
       ROUND(ps.damage_to_champions * 60.0000000000 / NULLIF(TIME_TO_SEC(s.play_time), 0), 2) AS dpm,
       CAST(ps.gold_at_15 AS SIGNED) - CAST(op_ps.gold_at_15 AS SIGNED) AS gold_diff_15,
       CAST(ps.cs_at_15   AS SIGNED) - CAST(op_ps.cs_at_15   AS SIGNED) AS cs_diff_15,
       CAST(ps.xp_at_15   AS SIGNED) - CAST(op_ps.xp_at_15   AS SIGNED) AS xp_diff_15,
       TIME_TO_SEC(s.play_time)                                         AS duration_sec,
       ps.total_points,
       LEAST(10, GREATEST(0, ROUND(6 + (CAST(ps.total_points AS SIGNED) - 50) / 12.5, 1))) AS rating,
       ps.is_pog
FROM player_set ps
JOIN team_set ts  ON ts.team_set_id = ps.team_set_id
JOIN match_set s  ON s.set_id = ts.set_id
JOIN lck_match m  ON m.match_id = s.match_id
JOIN stage g      ON g.stage_id = m.stage_id
JOIN player p     ON p.player_id = ps.player_id
JOIN team tm      ON tm.team_id = ts.team_id
JOIN champion c   ON c.champion_id = ps.champion_id
JOIN team op_tm   ON op_tm.team_id = IF(m.team1_id = ts.team_id, m.team2_id, m.team1_id)
LEFT JOIN team_set op_ts   ON op_ts.set_id = ts.set_id AND op_ts.team_id <> ts.team_id
LEFT JOIN player_set op_ps ON op_ps.team_set_id = op_ts.team_set_id AND op_ps.position = ps.position;

-- ---------------------------------------------------------------------
-- SCR-008 선수 종합: 시즌 요약 (한 행 = 선수 1명, v_player_games 집계)
-- 분당 지표와 15분 골드 차는 세트별 값(반올림 전)의 평균을 소수 2자리로 (서버 PlayerMetric과 같음).
-- 예) SELECT * FROM v_player_summary WHERE nickname = 'Faker';
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_player_summary AS
SELECT g.player_id, g.nickname,
       COUNT(*)                                                         AS sets,
       SUM(g.victory)                                                   AS wins,
       ROUND(SUM(g.victory) * 1.0000000000 / COUNT(*), 3)               AS win_rate,
       SUM(g.kills) AS kills, SUM(g.deaths) AS deaths, SUM(g.assists) AS assists,
       ROUND((SUM(g.kills) + SUM(g.assists)) * 1.0000000000 / GREATEST(SUM(g.deaths), 1), 2) AS kda,
       ROUND(AVG(g.cs * 60.0000000000 / NULLIF(g.duration_sec, 0)), 2)     AS cs_per_min,
       ROUND(AVG(g.damage * 60.0000000000 / NULLIF(g.duration_sec, 0)), 2) AS dpm,
       ROUND(AVG(g.gold_diff_15), 2)                                    AS gold_diff_15,
       ROUND(AVG(g.rating), 1)                                          AS avg_rating,
       MAX(g.rating)                                                    AS best_rating,
       SUM(g.is_pog)                                                    AS pog_count
FROM v_player_games g
GROUP BY g.player_id, g.nickname;

-- ---------------------------------------------------------------------
-- SCR-016 챔피언 통계: 픽·밴·승률 (한 행 = 챔피언 1개, 픽이나 밴이 한 번이라도 있는 챔피언)
-- pick_ban_rate = (픽 + 밴) ÷ 전체 세트 수, 소수 3자리 (서버와 같음)
-- 예) SELECT * FROM v_champion_stats ORDER BY pick_ban_rate DESC, picks DESC, champion LIMIT 10;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_champion_stats AS
SELECT c.champion_id, c.name AS champion,
       COALESCE(pk.picks, 0)                                                AS picks,
       COALESCE(pk.wins, 0)                                                 AS wins,
       ROUND(pk.wins * 1.0000000000 / NULLIF(pk.picks, 0), 3)               AS win_rate,
       ROUND((pk.kills + pk.assists) * 1.0000000000 / GREATEST(pk.deaths, 1), 2) AS kda,
       pk.avg_rating,
       COALESCE(bn.bans, 0)                                                 AS bans,
       ROUND((COALESCE(pk.picks, 0) + COALESCE(bn.bans, 0)) * 1.0000000000
             / NULLIF((SELECT COUNT(*) FROM match_set), 0), 3)              AS pick_ban_rate
FROM champion c
LEFT JOIN (
  SELECT ps.champion_id, COUNT(*) AS picks, SUM(ts.victory) AS wins,
         SUM(ps.kills) AS kills, SUM(ps.deaths) AS deaths, SUM(ps.assists) AS assists,
         ROUND(AVG(LEAST(10, GREATEST(0, ROUND(6 + (CAST(ps.total_points AS SIGNED) - 50) / 12.5, 1)))), 1) AS avg_rating
  FROM player_set ps
  JOIN team_set ts ON ts.team_set_id = ps.team_set_id
  GROUP BY ps.champion_id
) pk ON pk.champion_id = c.champion_id
LEFT JOIN (
  SELECT champion_id, COUNT(*) AS bans FROM ban GROUP BY champion_id
) bn ON bn.champion_id = c.champion_id
WHERE pk.picks IS NOT NULL OR bn.bans IS NOT NULL;

-- ---------------------------------------------------------------------
-- 확인: 만들어진 뷰 목록
-- ---------------------------------------------------------------------
SELECT TABLE_NAME AS view_name
FROM information_schema.VIEWS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;
-- 예상 결과: v_champion_stats, v_match_list, v_match_player_rating, v_match_score, v_player_games,
--           v_player_ranking, v_player_summary, v_set_ban, v_set_pog, v_set_scoreboard,
--           v_set_team, v_standings, v_team_roster (13개)

-- 지우기 (필요할 때만)
-- DROP VIEW IF EXISTS v_champion_stats, v_player_summary, v_player_games, v_player_ranking, v_team_roster,
--   v_standings, v_set_ban, v_set_scoreboard, v_set_team, v_match_player_rating, v_set_pog, v_match_list, v_match_score;
