-- =====================================================================
-- 03. 예외 상황: 제약 조건 위반은 DB가 막는다
--
-- 보여 주는 것: schema.sql의 CHECK, UNIQUE, FOREIGN KEY가 잘못된 데이터를 거부한다.
--   CSV 적재 스크립트에 버그가 있어도 잘못된 행은 DB에 들어가지 않는다.
-- 실행 계정: riftdata_loader (01에서 만든 계정. INSERT/UPDATE/DELETE 권한 필요)
--   docker exec -it riftdata-mysql mysql -uriftdata_loader -p riftdata
-- 예상 결과:
--   [준비]  시연용 행 INSERT 성공 (팀코드 EXA/EXB, 스테이지 순서 102, 챔피언 ExChampA/ExChampB)
--   [1~11] 전부 실패해야 정상. 각 쿼리 아래에 예상 오류 코드를 적었다.
--          3819 CHECK 위반 / 1062 UNIQUE 위반 / 1216 없는 부모 참조 / 1217 자식이 있는 부모 삭제
--          FK 오류 코드는 계정에 따라 다르다. root로 실행하면 1452/1451과 함께 FK 이름·부모 테이블이 보이고,
--          riftdata_loader처럼 DB 단위 권한만 있는 계정에는 부모 테이블 정보를 숨긴 1216/1217이 나온다
--          (MySQL은 부모 테이블에 테이블 단위 권한이 없는 계정에 자세한 FK 메시지를 보여 주지 않는다).
--   [정리]  시연용 행 삭제. 실데이터는 건드리지 않는다.
--
-- 파일 전체를 한 번에 실행하면 첫 오류에서 멈추므로, 시연에서는 블록 단위로 실행하거나 --force로 실행한다 (맥·Windows 공통):
--   docker cp sql/demo/03_exceptions.sql riftdata-mysql:/tmp/03.sql
--   docker exec -it riftdata-mysql sh -c "mysql --force -v -uriftdata_loader -p riftdata < /tmp/03.sql"
-- =====================================================================


-- 한글(스테이지·선수 이름)이 깨지지 않게 접속 문자셋을 맞춘다. 컨테이너 안 mysql 클라이언트의 기본값은 latin1이다.
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- [준비] 위반 대상이 될 정상 데이터 (경기 1, 세트 1, 팀세트 2, 선수세트 1, 밴 1)
-- ---------------------------------------------------------------------
-- 이전 시연에서 남은 행이 있으면 먼저 지운다 (경기를 지우면 세트·팀세트·선수세트·밴이 CASCADE로 지워진다)
DELETE FROM lck_match WHERE team1_id IN (SELECT team_id FROM team WHERE code IN ('EXA', 'EXB'));
DELETE FROM player WHERE nickname IN ('ExA_Mid', 'ExB_Mid');
DELETE FROM team WHERE code IN ('EXA', 'EXB');
DELETE FROM champion WHERE name IN ('ExChampA', 'ExChampB');
DELETE FROM stage WHERE stage_order = 102;

INSERT INTO stage (name, stage_order, start_date, end_date, is_regular)
VALUES ('EX 예외 시연', 102, '2026-12-01', '2026-12-31', 0);
SET @stage := LAST_INSERT_ID();
INSERT INTO team (code) VALUES ('EXA');
SET @ta := LAST_INSERT_ID();
INSERT INTO team (code) VALUES ('EXB');
SET @tb := LAST_INSERT_ID();
INSERT INTO champion (name) VALUES ('ExChampA');
SET @ca := LAST_INSERT_ID();
INSERT INTO champion (name) VALUES ('ExChampB');
SET @cb := LAST_INSERT_ID();
INSERT INTO player (team_id, nickname, position) VALUES (@ta, 'ExA_Mid', 'MID');
SET @pa := LAST_INSERT_ID();
INSERT INTO player (team_id, nickname, position) VALUES (@tb, 'ExB_Mid', 'MID');
SET @pb := LAST_INSERT_ID();

INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id)
VALUES ('2026-12-01 17:00:00', 3, @ta, @tb, @stage);
SET @m := LAST_INSERT_ID();
INSERT INTO match_set (match_id, set_number, game_id, version, play_time)
VALUES (@m, 1, 'EX-DEMO-0001', '26.22', '00:30:00');
SET @s := LAST_INSERT_ID();
INSERT INTO team_set (set_id, team_id, side, victory) VALUES (@s, @ta, 'BLUE', 1);
SET @tsa := LAST_INSERT_ID();
INSERT INTO team_set (set_id, team_id, side, victory) VALUES (@s, @tb, 'RED', 0);
SET @tsb := LAST_INSERT_ID();
INSERT INTO player_set (team_set_id, position, player_id, champion_id, total_points)
VALUES (@tsa, 'MID', @pa, @ca, 70);
INSERT INTO ban (team_set_id, ban_order, champion_id) VALUES (@tsa, 1, @cb);

-- 준비 확인: 경기 1, 세트 1, 팀세트 2, 선수세트 1, 밴 1
SELECT (SELECT COUNT(*) FROM match_set WHERE match_id = @m) AS sets,
       (SELECT COUNT(*) FROM team_set WHERE set_id = @s) AS team_sets,
       (SELECT COUNT(*) FROM player_set WHERE team_set_id IN (@tsa, @tsb)) AS player_sets,
       (SELECT COUNT(*) FROM ban WHERE team_set_id IN (@tsa, @tsb)) AS bans;


-- ---------------------------------------------------------------------
-- CHECK 위반 (schema.sql의 CHECK 5개를 하나씩)
-- ---------------------------------------------------------------------

-- 1) 같은 팀끼리 경기 (ck_match_teams: team1_id <> team2_id)
INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id)
VALUES ('2026-12-02 17:00:00', 3, @ta, @ta, @stage);
-- 예상: ERROR 3819 (HY000): Check constraint 'ck_match_teams' is violated.

-- 2) 6세트 (ck_set_number: set_number BETWEEN 1 AND 5)
INSERT INTO match_set (match_id, set_number, game_id, version, play_time)
VALUES (@m, 6, 'EX-DEMO-0006', '26.22', '00:30:00');
-- 예상: ERROR 3819 (HY000): Check constraint 'ck_set_number' is violated.

-- 3) 세트 점수 101점 (ck_player_set_points: total_points <= 100)
INSERT INTO player_set (team_set_id, position, player_id, champion_id, total_points)
VALUES (@tsb, 'MID', @pb, @ca, 101);
-- 예상: ERROR 3819 (HY000): Check constraint 'ck_player_set_points' is violated.

-- 4) 밴 6번째 (ck_ban_order: ban_order BETWEEN 1 AND 5)
INSERT INTO ban (team_set_id, ban_order, champion_id) VALUES (@tsb, 6, @ca);
-- 예상: ERROR 3819 (HY000): Check constraint 'ck_ban_order' is violated.

-- 5) 시즌 종합 점수 101점 (ck_season_points: 모든 점수 <= 100)
INSERT INTO player_season (player_id, sets, total_points, calculated_at)
VALUES (@pa, 10, 101, NOW());
-- 예상: ERROR 3819 (HY000): Check constraint 'ck_season_points' is violated.


-- ---------------------------------------------------------------------
-- UNIQUE 위반
-- ---------------------------------------------------------------------

-- 6) 같은 원본 게임ID로 세트를 한 번 더 적재 (uk_set_game)
INSERT INTO match_set (match_id, set_number, game_id, version, play_time)
VALUES (@m, 2, 'EX-DEMO-0001', '26.22', '00:30:00');
-- 예상: ERROR 1062 (23000): Duplicate entry 'EX-DEMO-0001' for key 'match_set.uk_set_game'

-- 7) 같은 팀세트에 MID가 두 명 (uk_player_set_position)
INSERT INTO player_set (team_set_id, position, player_id, champion_id, total_points)
VALUES (@tsa, 'MID', @pb, @cb, 60);
-- 예상: ERROR 1062 (23000): Duplicate entry '...-MID' for key 'player_set.uk_player_set_position'

-- 8) 같은 팀세트에 같은 선수가 다른 포지션으로 한 번 더 (uk_player_set_player)
INSERT INTO player_set (team_set_id, position, player_id, champion_id, total_points)
VALUES (@tsa, 'TOP', @pa, @cb, 60);
-- 예상: ERROR 1062 (23000): Duplicate entry '...' for key 'player_set.uk_player_set_player'

-- 9) 같은 팀세트에 같은 밴 순서 (uk_ban_order)
INSERT INTO ban (team_set_id, ban_order, champion_id) VALUES (@tsa, 1, @ca);
-- 예상: ERROR 1062 (23000): Duplicate entry '...-1' for key 'ban.uk_ban_order'


-- ---------------------------------------------------------------------
-- FOREIGN KEY 위반
-- ---------------------------------------------------------------------

-- 10) 없는 팀 ID로 경기 등록 (fk_match_team2)
INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id)
VALUES ('2026-12-02 17:00:00', 3, @ta, 999999999, @stage);
-- 예상 (riftdata_loader): ERROR 1216 (23000): Cannot add or update a child row: a foreign key constraint fails
-- 참고 (root):             ERROR 1452 (23000): ... (`riftdata`.`lck_match`, CONSTRAINT `fk_match_team2` FOREIGN KEY (`team2_id`) REFERENCES `team` ...)

-- 11) 경기가 있는 팀 삭제 (fk_match_team1 ON DELETE RESTRICT)
DELETE FROM team WHERE team_id = @ta;
-- 예상 (riftdata_loader): ERROR 1217 (23000): Cannot delete or update a parent row: a foreign key constraint fails
-- 참고 (root):             ERROR 1451 (23000): ... (`riftdata`.`lck_match`, CONSTRAINT `fk_match_team1` FOREIGN KEY (`team1_id`) REFERENCES `team` ...)


-- ---------------------------------------------------------------------
-- 위반 시도 후 확인: 1~11이 아무 행도 남기지 않았다 → 준비 확인과 같은 값 (1, 2, 1, 1)
-- ---------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM match_set WHERE match_id = @m) AS sets,
       (SELECT COUNT(*) FROM team_set WHERE set_id = @s) AS team_sets,
       (SELECT COUNT(*) FROM player_set WHERE team_set_id IN (@tsa, @tsb)) AS player_sets,
       (SELECT COUNT(*) FROM ban WHERE team_set_id IN (@tsa, @tsb)) AS bans;
SELECT COUNT(*) AS ex_matches FROM lck_match WHERE stage_id = @stage;   -- 1


-- ---------------------------------------------------------------------
-- 참고: 스키마에 없는 제약 (시연에서는 실행하지 않는다)
--   lck_match.sets(세트 수)에는 CHECK가 없어 3·5가 아닌 값도 들어간다.
--   막으려면 schema.sql에 CONSTRAINT ck_match_sets CHECK (sets IN (3, 5))를 추가해야 한다.
-- ---------------------------------------------------------------------
-- START TRANSACTION;
-- INSERT INTO lck_match (match_time, sets, team1_id, team2_id, stage_id)
-- VALUES ('2026-12-02 17:00:00', 4, @ta, @tb, @stage);   -- 현재 스키마에서는 성공한다
-- ROLLBACK;


-- ---------------------------------------------------------------------
-- [정리] 시연용 행 삭제 (경기 → CASCADE로 세트·팀세트·선수세트·밴 삭제, 그다음 부모 행)
-- ---------------------------------------------------------------------
DELETE FROM lck_match WHERE stage_id = @stage;
DELETE FROM player WHERE nickname IN ('ExA_Mid', 'ExB_Mid');
DELETE FROM team WHERE code IN ('EXA', 'EXB');
DELETE FROM champion WHERE name IN ('ExChampA', 'ExChampB');
DELETE FROM stage WHERE stage_order = 102;
SELECT COUNT(*) AS ex_teams_left FROM team WHERE code IN ('EXA', 'EXB');   -- 0
