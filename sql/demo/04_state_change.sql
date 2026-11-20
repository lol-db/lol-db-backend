-- =====================================================================
-- 04. 데이터 상태 변경이 서비스에 바로 반영되는 것
--
-- 보여 주는 것:
--   (a) 경기 1건 INSERT              → 경기 목록 API에 바로 나타남 (예정 경기)
--   (b) 선수 세트 기록 UPDATE + 재계산 → 선수 랭킹의 평균 평점·종합 점수·등급·순위가 바뀜
--   (c) 경기 1건 DELETE              → ON DELETE CASCADE로 세트·팀세트·선수세트·밴이 함께 삭제됨
-- 실행 계정: riftdata_loader (01에서 만든 계정)
--   docker exec -it riftdata-mysql mysql -uriftdata_loader -p riftdata
--   세션 변수(@ta, @m1 등)를 쓰므로 [준비]부터 같은 접속에서 순서대로 실행한다.
--   API 확인용 서버: ./gradlew bootRun (조회 전용 계정으로 띄우려면 README의 "서버 실행" 참고)
-- 예상 결과: 각 단계 아래 주석. 마지막 [정리]를 실행하면 시연 전 상태로 돌아간다.
--
-- 재계산에 대해: 명세서상 세트 점수(B3, player_set.total_points)와 시즌 지표(B4, player_season)는
--   CSV 적재 뒤 재계산 프로그램이 채운다 (docs/csv-loader.md, --loader.recalculate-only=true).
--   재계산은 시즌 전체 같은 포지션 분포로 점수를 다시 매겨 시연용 값이 실데이터에 따라 달라지므로,
--   (b)는 재계산 결과가 들어가는 두 컬럼을 직접 UPDATE해서 "재계산이 끝난 상태"를 예측 가능하게 만든다.
--   서버는 저장된 값을 읽기만 하므로(평점은 total_points로 조회 때 계산) UPDATE 즉시 API 응답이 바뀐다.
--
-- 시연용 행만 쓴다: 팀코드 DMA/DMB, 선수 DemoA_Mid/DemoB_Mid, 챔피언 DemoChamp, 스테이지 순서 101.
-- =====================================================================


-- 한글(스테이지·선수 이름)이 깨지지 않게 접속 문자셋을 맞춘다. 컨테이너 안 mysql 클라이언트의 기본값은 latin1이다.
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- [정리 먼저] 이전 시연에서 남은 행이 있으면 지운다 (맨 아래 [정리]와 같음)
-- ---------------------------------------------------------------------
DELETE FROM lck_match WHERE team1_id IN (SELECT team_id FROM team WHERE code IN ('DMA', 'DMB'));
DELETE FROM player WHERE nickname IN ('DemoA_Mid', 'DemoB_Mid');
DELETE FROM team WHERE code IN ('DMA', 'DMB');
DELETE FROM champion WHERE name = 'DemoChamp';
DELETE FROM stage WHERE stage_order = 101;


-- ---------------------------------------------------------------------
-- [준비] 두 팀이 Bo5 2경기(10세트)를 치른 상태. 선수 랭킹·등급은 10세트 이상이어야 나온다.
--   경기1: DMA 3:2 승 / 경기2: DMB 3:2 승. 각 팀 MID 선수 1명씩 10세트 출전.
--   세트 점수: DemoA 55점(평점 6.4), DemoB 75점(평점 8.0)
--   시즌 지표: DemoA 60점(B+), DemoB 80점(A-)
-- ---------------------------------------------------------------------
INSERT INTO stage (name, stage_order, start_date, end_date, is_regular)
VALUES ('DEMO 시연', 101, '2026-11-01', '2026-11-30', 1);
SET @stage := LAST_INSERT_ID();
INSERT INTO team (code) VALUES ('DMA');
SET @ta := LAST_INSERT_ID();
INSERT INTO team (code) VALUES ('DMB');
SET @tb := LAST_INSERT_ID();
INSERT INTO champion (name) VALUES ('DemoChamp');
SET @champ := LAST_INSERT_ID();
INSERT INTO player (team_id, nickname, position, name) VALUES (@ta, 'DemoA_Mid', 'MID', '시연A');
SET @pa := LAST_INSERT_ID();
INSERT INTO player (team_id, nickname, position, name) VALUES (@tb, 'DemoB_Mid', 'MID', '시연B');
SET @pb := LAST_INSERT_ID();

INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id)
VALUES ('2026-11-02 17:00:00', 5, @ta, @tb, @stage);
SET @m1 := LAST_INSERT_ID();
INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id)
VALUES ('2026-11-03 17:00:00', 5, @ta, @tb, @stage);
SET @m2 := LAST_INSERT_ID();

-- 경기마다 1~5세트
INSERT INTO match_set (match_id, set_number, game_id, version, play_time)
SELECT m.match_id, n.no, CONCAT('DEMO-', m.match_id, '-', n.no), '26.22', '00:30:00'
FROM lck_match m
CROSS JOIN (SELECT 1 AS no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) n
WHERE m.match_id IN (@m1, @m2);

-- 홀수 세트는 경기1에서 DMA 승, 경기2에서 DMB 승 → 각 경기 3:2
INSERT INTO team_set (set_id, team_id, side, victory, first_dragon, dragons, towers, barons)
SELECT s.set_id, @ta, 'BLUE', (s.set_number % 2 = 1) = (s.match_id = @m1), 1, 3, 8, 1
FROM match_set s WHERE s.match_id IN (@m1, @m2);
INSERT INTO team_set (set_id, team_id, side, victory, dragons, towers)
SELECT s.set_id, @tb, 'RED', (s.set_number % 2 = 1) <> (s.match_id = @m1), 1, 3
FROM match_set s WHERE s.match_id IN (@m1, @m2);

INSERT INTO player_set (team_set_id, position, player_id, champion_id, kills, deaths, assists,
                        damage_to_champions, vision_points, total_golds, earned_golds,
                        minion_kills, monster_kills, gold_at_15, total_points)
SELECT ts.team_set_id, 'MID', IF(ts.team_id = @ta, @pa, @pb), @champ, 5, 2, 6,
       21000, 40, 14000, 9000, 260, 10, 6000, IF(ts.team_id = @ta, 55, 75)
FROM team_set ts JOIN match_set s ON s.set_id = ts.set_id
WHERE s.match_id IN (@m1, @m2);

-- (c)에서 CASCADE를 보여 주려고 경기1의 팀세트마다 밴 1개
INSERT INTO ban (team_set_id, ban_order, champion_id)
SELECT ts.team_set_id, 1, @champ
FROM team_set ts JOIN match_set s ON s.set_id = ts.set_id
WHERE s.match_id = @m1;

-- 시즌 지표 (B4 재계산 결과 자리)
INSERT INTO player_season (player_id, sets, total_points, combat_points, damage_points, laning_points,
                           growth_points, ward_points, survival_points, rank_in_position, calculated_at)
VALUES (@pa, 10, 60, 60, 60, 60, 60, 60, 60, 2, NOW()),
       (@pb, 10, 80, 80, 80, 80, 80, 80, 80, 1, NOW());

-- 준비 확인: 경기 2, 세트 10, 팀세트 20, 선수세트 20, 밴 10
SELECT (SELECT COUNT(*) FROM lck_match WHERE stage_id = @stage) AS matches,
       (SELECT COUNT(*) FROM match_set s JOIN lck_match m ON m.match_id = s.match_id WHERE m.stage_id = @stage) AS sets,
       (SELECT COUNT(*) FROM team_set WHERE team_id IN (@ta, @tb)) AS team_sets,
       (SELECT COUNT(*) FROM player_set WHERE player_id IN (@pa, @pb)) AS player_sets,
       (SELECT COUNT(*) FROM ban b JOIN team_set ts ON ts.team_set_id = b.team_set_id WHERE ts.team_id IN (@ta, @tb)) AS bans;

-- 화면/API 확인 (준비 상태)
--   GET http://localhost:8080/api/v1/players?q=Demo
--     → 1위 DemoB_Mid(overallScore 80.0, A-, avgRating 8.0), 2위 DemoA_Mid(60.0, B+, avgRating 6.4)


-- =====================================================================
-- (a) 새 경기 1건 INSERT → 경기 목록에 바로 보인다
-- =====================================================================
INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id)
VALUES ('2026-11-20 20:00:00', 3, @tb, @ta, @stage);
SET @new_match := LAST_INSERT_ID();

-- 확인 (DB)
SELECT m.match_id, m.match_time, m.sets, t1.code AS team1, t2.code AS team2
FROM lck_match m JOIN team t1 ON t1.team_id = m.team1_id JOIN team t2 ON t2.team_id = m.team2_id
WHERE m.match_id = @new_match;

-- 확인 (API)
--   GET http://localhost:8080/api/v1/matches?from=2026-11-20&to=2026-11-20
--     → 방금 넣은 경기 1건. 세트 기록이 없어 status=SCHEDULED, 점수 null
--   GET http://localhost:8080/api/v1/matches/calendar?month=2026-11
--     → 11/20에 matchCount 1 추가


-- =====================================================================
-- (b) 선수 세트 기록 UPDATE → 재계산 → 랭킹·등급이 바뀐다
-- =====================================================================
-- 변경 전
SELECT p.nickname, COUNT(*) AS sets, ROUND(AVG(ps.total_points), 1) AS avg_set_points,
       s.total_points AS season_points, s.rank_in_position
FROM player p
JOIN player_set ps ON ps.player_id = p.player_id
JOIN player_season s ON s.player_id = p.player_id
WHERE p.player_id IN (@pa, @pb)
GROUP BY p.nickname, s.total_points, s.rank_in_position
ORDER BY s.total_points DESC;
-- 예상: DemoB_Mid 10세트·75·80·1위 / DemoA_Mid 10세트·55·60·2위

-- 1단계: 원천 기록 수정 (예: 적재 오류로 DemoA의 경기2 킬·어시스트가 잘못 들어가 있었다고 가정)
UPDATE player_set ps
JOIN team_set ts ON ts.team_set_id = ps.team_set_id
JOIN match_set s ON s.set_id = ts.set_id
SET ps.kills = 12, ps.assists = 10
WHERE ps.player_id = @pa AND s.match_id = @m2;
-- 예상: 5 rows affected

-- 2단계: 재계산 (B3 세트 점수 → B4 시즌 지표)
--   시연 값을 고정하려고 결과 값을 직접 넣는다. 실제 재계산을 보여 주려면 아래 두 UPDATE 대신
--   ./gradlew bootRun --args="--spring.profiles.active=loader --loader.recalculate-only=true" 를 실행한다
--   (시즌 전체를 다시 계산하므로 점수는 실데이터 분포에 따라 정해진다).
UPDATE player_set SET total_points = 95 WHERE player_id = @pa;   -- B3: DemoA 세트 점수 55 → 95 (평점 6.4 → 9.6)
UPDATE player_season
SET total_points = IF(player_id = @pa, 90, 80),
    combat_points = IF(player_id = @pa, 90, 80),
    rank_in_position = IF(player_id = @pa, 1, 2),
    calculated_at = NOW()
WHERE player_id IN (@pa, @pb);                                    -- B4: DemoA 60 → 90, 순위 1 ↔ 2

-- 변경 후 (위 "변경 전"과 같은 쿼리)
SELECT p.nickname, COUNT(*) AS sets, ROUND(AVG(ps.total_points), 1) AS avg_set_points,
       s.total_points AS season_points, s.rank_in_position
FROM player p
JOIN player_set ps ON ps.player_id = p.player_id
JOIN player_season s ON s.player_id = p.player_id
WHERE p.player_id IN (@pa, @pb)
GROUP BY p.nickname, s.total_points, s.rank_in_position
ORDER BY s.total_points DESC;
-- 예상: DemoA_Mid 10세트·95·90·1위 / DemoB_Mid 10세트·75·80·2위

-- 확인 (API)
--   GET http://localhost:8080/api/v1/players?q=Demo
--     → 1위 DemoA_Mid(overallScore 90.0, 등급 B+ → A, avgRating 6.4 → 9.6), 2위 DemoB_Mid
--   GET http://localhost:8080/api/v1/players/{DemoA의 playerId}   (playerId는 아래 SELECT로 확인)
--     → summary.kills 50 → 85, avgRating 9.6, grade A
SELECT @pa AS demo_a_player_id, @pb AS demo_b_player_id;


-- =====================================================================
-- (c) 경기 1건 DELETE → CASCADE로 하위 기록이 함께 지워진다
--   lck_match ─CASCADE→ match_set ─CASCADE→ team_set ─CASCADE→ player_set, ban
-- =====================================================================
-- 삭제 전 (경기1의 하위 행 수)
SELECT (SELECT COUNT(*) FROM match_set WHERE match_id = @m1) AS sets,
       (SELECT COUNT(*) FROM team_set ts JOIN match_set s ON s.set_id = ts.set_id WHERE s.match_id = @m1) AS team_sets,
       (SELECT COUNT(*) FROM player_set ps JOIN team_set ts ON ts.team_set_id = ps.team_set_id
          JOIN match_set s ON s.set_id = ts.set_id WHERE s.match_id = @m1) AS player_sets,
       (SELECT COUNT(*) FROM ban b JOIN team_set ts ON ts.team_set_id = b.team_set_id
          JOIN match_set s ON s.set_id = ts.set_id WHERE s.match_id = @m1) AS bans;
-- 예상: 5, 10, 10, 10

-- 시연 데이터 전체 행 수 (삭제 전)
SELECT (SELECT COUNT(*) FROM match_set s JOIN lck_match m ON m.match_id = s.match_id WHERE m.stage_id = @stage) AS sets,
       (SELECT COUNT(*) FROM team_set WHERE team_id IN (@ta, @tb)) AS team_sets,
       (SELECT COUNT(*) FROM player_set WHERE player_id IN (@pa, @pb)) AS player_sets,
       (SELECT COUNT(*) FROM ban b JOIN team_set ts ON ts.team_set_id = b.team_set_id WHERE ts.team_id IN (@ta, @tb)) AS bans;
-- 예상: 10, 20, 20, 10

-- 확인 (API, 삭제 전): GET http://localhost:8080/api/v1/matches/{@m1}  → 200, 세트 5개
SELECT @m1 AS match_to_delete;

DELETE FROM lck_match WHERE match_id = @m1;
-- 예상: 1 row affected (MySQL은 CASCADE로 지워진 자식 행을 이 숫자에 세지 않는다)

-- 삭제 후 (경기1의 하위 행 수) → 0, 0, 0, 0
SELECT (SELECT COUNT(*) FROM match_set WHERE match_id = @m1) AS sets,
       (SELECT COUNT(*) FROM team_set ts JOIN match_set s ON s.set_id = ts.set_id WHERE s.match_id = @m1) AS team_sets,
       (SELECT COUNT(*) FROM player_set ps JOIN team_set ts ON ts.team_set_id = ps.team_set_id
          JOIN match_set s ON s.set_id = ts.set_id WHERE s.match_id = @m1) AS player_sets,
       (SELECT COUNT(*) FROM ban b JOIN team_set ts ON ts.team_set_id = b.team_set_id
          JOIN match_set s ON s.set_id = ts.set_id WHERE s.match_id = @m1) AS bans;

-- 시연 데이터 전체 행 수 (삭제 후) → 5, 10, 10, 0  (경기2 기록만 남음)
SELECT (SELECT COUNT(*) FROM match_set s JOIN lck_match m ON m.match_id = s.match_id WHERE m.stage_id = @stage) AS sets,
       (SELECT COUNT(*) FROM team_set WHERE team_id IN (@ta, @tb)) AS team_sets,
       (SELECT COUNT(*) FROM player_set WHERE player_id IN (@pa, @pb)) AS player_sets,
       (SELECT COUNT(*) FROM ban b JOIN team_set ts ON ts.team_set_id = b.team_set_id WHERE ts.team_id IN (@ta, @tb)) AS bans;

-- 확인 (API, 삭제 후)
--   GET http://localhost:8080/api/v1/matches/{@m1}                 → 404 MATCH_002
--   GET http://localhost:8080/api/v1/matches?from=2026-11-02&to=2026-11-03 → 경기2만 남음
--   GET http://localhost:8080/api/v1/players?q=Demo                → 두 선수 모두 5세트라 10세트 미만, 기본 목록에서 빠짐
--   GET http://localhost:8080/api/v1/players?q=Demo&includeLowSample=true → lowSample=true, rank·등급 null


-- =====================================================================
-- [정리] 원상 복구
--   방법 1: 00_backup_restore.md의 복원 (시연 전 백업이 있을 때, 가장 확실함)
--   방법 2: 아래 쿼리 (시연용 행만 지움. 실데이터는 그대로)
-- =====================================================================
DELETE FROM lck_match WHERE team1_id IN (SELECT team_id FROM team WHERE code IN ('DMA', 'DMB'));  -- 하위 기록 CASCADE
DELETE FROM player WHERE nickname IN ('DemoA_Mid', 'DemoB_Mid');                                  -- player_season CASCADE
DELETE FROM team WHERE code IN ('DMA', 'DMB');
DELETE FROM champion WHERE name = 'DemoChamp';
DELETE FROM stage WHERE stage_order = 101;
SELECT COUNT(*) AS demo_teams_left FROM team WHERE code IN ('DMA', 'DMB');   -- 0
