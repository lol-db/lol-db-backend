-- =====================================================================
-- 02. 조회 전용 계정 권한 검증
--
-- 보여 주는 것: 서비스 서버 계정(riftdata_app)은 조회만 되고, 데이터를 바꾸는 명령은 DB가 거부한다.
--   서버 코드에 버그가 있거나 SQL 인젝션이 생겨도 이 계정으로는 데이터를 바꿀 수 없다.
-- 실행 계정: riftdata_app (01에서 만든 계정)
--   docker exec -it riftdata-mysql mysql -uriftdata_app -p riftdata
-- 예상 결과:
--   1) SELECT                   → 성공
--   2) INSERT / UPDATE / DELETE → ERROR 1142 (42000): ... command denied to user 'riftdata_app'@'...' for table '...'
--   3) DROP TABLE               → ERROR 1142
--   실패한 명령은 아무것도 바꾸지 않으므로 이 파일은 데이터에 영향이 없다.
--
-- 파일 전체를 한 번에 실행하면 mysql 클라이언트가 첫 오류에서 멈춘다. 시연에서는 한 줄씩 실행하거나
-- --force로 실행한다 (맥·Windows PowerShell 공통):
--   docker cp sql/demo/02_privilege_check.sql riftdata-mysql:/tmp/02.sql
--   docker exec -it riftdata-mysql sh -c "mysql --force -v -uriftdata_app -p riftdata < /tmp/02.sql"
-- =====================================================================

-- 한글(스테이지·선수 이름)이 깨지지 않게 접속 문자셋을 맞춘다. 컨테이너 안 mysql 클라이언트의 기본값은 latin1이다.
SET NAMES utf8mb4;

-- 현재 접속 계정 확인 → riftdata_app@%
SELECT CURRENT_USER();

-- 1) 조회: 성공
SELECT COUNT(*) AS team_count FROM team;
SELECT team_id, code FROM team ORDER BY code LIMIT 5;

-- 2) 변경: 모두 ERROR 1142
INSERT INTO team (code) VALUES ('HACK');
-- 예상: ERROR 1142 (42000): INSERT command denied to user 'riftdata_app'@'...' for table 'team'

UPDATE team SET code = 'HACK' WHERE team_id = 1;
-- 예상: ERROR 1142 (42000): UPDATE command denied to user 'riftdata_app'@'...' for table 'team'

DELETE FROM team WHERE team_id = 1;
-- 예상: ERROR 1142 (42000): DELETE command denied to user 'riftdata_app'@'...' for table 'team'

-- 3) 구조 변경: ERROR 1142
DROP TABLE ban;
-- 예상: ERROR 1142 (42000): DROP command denied to user 'riftdata_app'@'...' for table 'ban'

-- 아무것도 바뀌지 않았는지 다시 조회 → 1)과 같은 결과
SELECT COUNT(*) AS team_count FROM team;
