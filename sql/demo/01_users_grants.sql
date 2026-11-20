-- =====================================================================
-- 01. DB 계정 권한 분리
--
-- 보여 주는 것: 서비스 서버는 조회 전용 계정, 데이터 적재·재계산은 별도 계정으로 나눈다.
--   로그인 기능이 없는 서비스라 "권한 검증"을 DB 계정 권한으로 보여 준다.
--   - riftdata_app    : SELECT만. Spring Boot 서버용 (API가 전부 GET이라 조회만 필요)
--   - riftdata_loader : SELECT, INSERT, UPDATE, DELETE. CSV 적재·재계산용 (테이블 구조 변경 권한은 없음)
-- 실행 계정: root
--   docker exec -it riftdata-mysql mysql -uroot -p
--   (비밀번호는 docker-compose.yml의 DB_ROOT_PASSWORD, 기본값 root)
-- 예상 결과: 계정 2개 생성 → SHOW GRANTS에 아래 권한만 보임
--   riftdata_app    : GRANT USAGE ON *.*  /  GRANT SELECT ON `riftdata`.*
--   riftdata_loader : GRANT USAGE ON *.*  /  GRANT SELECT, INSERT, UPDATE, DELETE ON `riftdata`.*
--
-- DB 이름이 riftdata가 아니면 아래 `riftdata`를 .env의 DB_NAME으로 바꾼다.
-- 서버를 이 계정으로 띄우려면 .env에 DB_APP_USERNAME=riftdata_app, DB_APP_PASSWORD=(아래 비밀번호)를 넣는다.
-- =====================================================================

-- ※ 비밀번호는 예시값이다. 발표 전 반드시 바꾸고, 실제 비밀번호는 저장소에 올리지 않는다.
--   '%'는 어느 호스트에서든 접속 가능하다는 뜻이다. 서버(호스트 PC)는 Docker 밖에서 접속하므로 localhost만으로는 부족하다.
CREATE USER IF NOT EXISTS 'riftdata_app'@'%'    IDENTIFIED BY 'change-me-app';     -- ← 바꿀 것
CREATE USER IF NOT EXISTS 'riftdata_loader'@'%' IDENTIFIED BY 'change-me-loader';  -- ← 바꿀 것

-- 서비스 서버: 조회만
GRANT SELECT ON riftdata.* TO 'riftdata_app'@'%';

-- 적재·재계산: 행 단위 변경만. CREATE/DROP/ALTER 같은 구조 변경은 주지 않는다.
GRANT SELECT, INSERT, UPDATE, DELETE ON riftdata.* TO 'riftdata_loader'@'%';

-- 권한 확인
SHOW GRANTS FOR 'riftdata_app'@'%';
SHOW GRANTS FOR 'riftdata_loader'@'%';

-- 계정 목록 확인
SELECT user, host FROM mysql.user WHERE user LIKE 'riftdata\_%' ORDER BY user;


-- ---------------------------------------------------------------------
-- (시연 후 선택) 계정 삭제. 다음 시연에 다시 쓸 거면 실행하지 않는다.
-- ---------------------------------------------------------------------
-- DROP USER IF EXISTS 'riftdata_app'@'%', 'riftdata_loader'@'%';
