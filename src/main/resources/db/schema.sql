-- RIFT DATA 테이블 DDL (MySQL 8.0, InnoDB, utf8mb4_unicode_ci)
--
-- 재호의 「물리DB 설계」 수정본(11/9)을 옮겼다.
--   식별 관계는 모두 비식별 + 대리키(BIGINT AUTO_INCREMENT)로 바꾸고, 원래 식별자 조합은 UNIQUE로 둔다.
--   선수세트시간대별지표는 player_set의 컬럼(10·15·20·25분 × 6개)으로 합쳤다.
-- 원본 DDL(RIFT_DATA_DDL_최종.sql)을 받으면 이 파일을 그것으로 바꾼다.
-- 뷰 4개(v_match_score, v_standings, v_team_set, v_player_set)와 스테이지 초기 데이터는 아직 없다.
--
-- 빈 데이터베이스에 한 번만 실행한다:
--   mysql -h 127.0.0.1 -u root -p riftdata < src/main/resources/db/schema.sql

SET NAMES utf8mb4;

-- 1. 팀
CREATE TABLE team (
    team_id BIGINT     NOT NULL AUTO_INCREMENT COMMENT '팀ID',
    code    VARCHAR(4) NOT NULL COMMENT '팀코드 (T1, GEN)',
    PRIMARY KEY (team_id),
    UNIQUE KEY uk_team_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '팀';

-- 2. 선수
CREATE TABLE player (
    player_id BIGINT                                    NOT NULL AUTO_INCREMENT COMMENT '선수ID',
    team_id   BIGINT                                    NOT NULL COMMENT '팀ID',
    nickname  VARCHAR(15)                               NOT NULL COMMENT '프로게이머 닉네임',
    position  ENUM ('TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT') NOT NULL COMMENT '주포지션',
    name      VARCHAR(15)                               NULL COMMENT '이름 (CSV에 없음, 직접 입력)',
    PRIMARY KEY (player_id),
    UNIQUE KEY uk_player_nickname (nickname),
    KEY ix_player_position (position),
    CONSTRAINT fk_player_team FOREIGN KEY (team_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '선수';

-- 3. 챔피언
CREATE TABLE champion (
    champion_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT '챔피언ID',
    name        VARCHAR(20) NOT NULL COMMENT '이름 (CSV champion)',
    PRIMARY KEY (champion_id),
    UNIQUE KEY uk_champion_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '챔피언';

-- 4. 스테이지 (ORDER는 예약어라 stage_order)
CREATE TABLE stage (
    stage_id    BIGINT           NOT NULL AUTO_INCREMENT COMMENT '스테이지ID',
    name        VARCHAR(20)      NOT NULL COMMENT '스테이지명',
    stage_order TINYINT UNSIGNED NOT NULL COMMENT '진행순서',
    start_date  DATE             NOT NULL COMMENT '시작일자',
    end_date    DATE             NOT NULL COMMENT '종료일자',
    is_regular  BOOLEAN          NOT NULL COMMENT '정규여부 (순위표 집계 대상)',
    PRIMARY KEY (stage_id),
    UNIQUE KEY uk_stage_order (stage_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '스테이지';

-- 5. 경기 (MATCH는 예약어라 lck_match)
CREATE TABLE lck_match (
    match_id   BIGINT           NOT NULL AUTO_INCREMENT COMMENT '경기ID',
    match_time DATETIME         NOT NULL COMMENT '경기일시',
    sets       TINYINT UNSIGNED NOT NULL COMMENT '세트 수 (bo3, bo5)',
    team1_id   BIGINT           NOT NULL COMMENT '팀1 ID',
    team2_id   BIGINT           NOT NULL COMMENT '팀2 ID',
    stage_id   BIGINT           NOT NULL COMMENT '스테이지ID',
    PRIMARY KEY (match_id),
    KEY ix_match_time (match_time),
    CONSTRAINT fk_match_team1 FOREIGN KEY (team1_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_match_team2 FOREIGN KEY (team2_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_match_stage FOREIGN KEY (stage_id) REFERENCES stage (stage_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_match_teams CHECK (team1_id <> team2_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '경기';

-- 6. 세트 (SET은 예약어라 match_set)
CREATE TABLE match_set (
    set_id     BIGINT           NOT NULL AUTO_INCREMENT COMMENT '세트ID',
    match_id   BIGINT           NOT NULL COMMENT '경기ID',
    set_number TINYINT UNSIGNED NOT NULL COMMENT '세트 번호 1~5',
    game_id    VARCHAR(40)      NOT NULL COMMENT '원본게임ID (CSV gameid)',
    version    VARCHAR(15)      NOT NULL COMMENT '패치버전',
    play_time  TIME             NOT NULL COMMENT '경기시간',
    PRIMARY KEY (set_id),
    UNIQUE KEY uk_set_match_number (match_id, set_number),
    UNIQUE KEY uk_set_game (game_id),
    KEY ix_set_version (version),
    CONSTRAINT fk_set_match FOREIGN KEY (match_id) REFERENCES lck_match (match_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_set_number CHECK (set_number BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '세트';

-- 7. 팀세트기록
CREATE TABLE team_set (
    team_set_id      BIGINT              NOT NULL AUTO_INCREMENT COMMENT '팀세트기록ID',
    set_id           BIGINT              NOT NULL COMMENT '세트ID',
    team_id          BIGINT              NOT NULL COMMENT '팀ID',
    side             ENUM ('BLUE', 'RED') NOT NULL COMMENT '진영',
    victory          BOOLEAN             NOT NULL COMMENT '승리여부',
    first_blood      BOOLEAN             NOT NULL DEFAULT 0 COMMENT '선취점',
    first_dragon     BOOLEAN             NOT NULL DEFAULT 0 COMMENT '첫 용',
    first_herald     BOOLEAN             NOT NULL DEFAULT 0 COMMENT '첫 전령',
    first_baron      BOOLEAN             NOT NULL DEFAULT 0 COMMENT '첫 바론',
    first_tower      BOOLEAN             NOT NULL DEFAULT 0 COMMENT '첫 포탑',
    first_mid_tower  BOOLEAN             NOT NULL DEFAULT 0 COMMENT '첫 미드 포탑',
    dragons          TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '용 개수',
    fire_dragons     TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '화염 용 개수',
    water_dragons    TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '물 용 개수',
    earth_dragons    TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '대지 용 개수',
    wind_dragons     TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '바람 용 개수',
    hextech_dragons  TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '마법공학 용 개수',
    chemtech_dragons TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '화학공학 용 개수',
    void_grubs       TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '공허유충 개수',
    barons           TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '바론 개수',
    towers           TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '포탑 개수',
    tower_plates     TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '포탑방패 개수',
    inhibitors       TINYINT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '억제기 개수',
    PRIMARY KEY (team_set_id),
    UNIQUE KEY uk_team_set_team (set_id, team_id),
    UNIQUE KEY uk_team_set_side (set_id, side),
    CONSTRAINT fk_team_set_set FOREIGN KEY (set_id) REFERENCES match_set (set_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_team_set_team FOREIGN KEY (team_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '팀세트기록';

-- 8. 선수세트지표 (+ 선수세트시간대별지표 반정규화)
CREATE TABLE player_set (
    player_set_id        BIGINT                                    NOT NULL AUTO_INCREMENT COMMENT '선수세트지표ID',
    team_set_id          BIGINT                                    NOT NULL COMMENT '팀세트기록ID',
    position             ENUM ('TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT') NOT NULL COMMENT '포지션',
    player_id            BIGINT                                    NOT NULL COMMENT '선수ID',
    champion_id          BIGINT                                    NOT NULL COMMENT '챔피언ID',
    kills                TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '킬 수',
    deaths               TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '데스 수',
    assists              TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '어시스트 수',
    double_kills         TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '더블킬 수',
    triple_kills         TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '트리플킬 수',
    quadra_kills         TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '쿼드라킬 수',
    penta_kills          TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '펜타킬 수',
    first_blood_kill     BOOLEAN           NOT NULL DEFAULT 0 COMMENT '선취점 킬',
    first_blood_assist   BOOLEAN           NOT NULL DEFAULT 0 COMMENT '선취점 어시스트',
    first_blood_death    BOOLEAN           NOT NULL DEFAULT 0 COMMENT '선취점 데스',
    damage_to_champions  INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '챔피언에게 가한 피해량',
    damage_taken_pm      INT UNSIGNED      NULL COMMENT '받은 피해량 (분당)',
    damage_mitigated_pm  INT UNSIGNED      NULL COMMENT '경감한 피해량 (분당)',
    damage_to_buildings  INT UNSIGNED      NULL COMMENT '건물에게 가한 피해량',
    vision_points        SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '시야점수',
    wards_placed         INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '설치한 와드 수 (서포터 136 실측, TINYINT로는 매핑 불가)',
    wards_removed        TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '제거한 와드 수',
    control_wards_bought TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '제어와드 구매 수',
    total_golds          INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '총 골드',
    earned_golds         INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '획득한 골드',
    gold_spent           INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '사용한 골드',
    minion_kills         SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '미니언 처치 수',
    monster_kills        SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '몬스터 처치 수',
    total_points         TINYINT UNSIGNED  NULL COMMENT '총 점수 0~100 (유도, 적재 후 B3에서 계산)',
    is_pog               BOOLEAN           NOT NULL DEFAULT 0 COMMENT 'POG 여부 (유도)',
    gold_at_10           INT UNSIGNED      NULL COMMENT '10분 골드',
    xp_at_10             INT UNSIGNED      NULL COMMENT '10분 경험치',
    cs_at_10             SMALLINT UNSIGNED NULL COMMENT '10분 CS',
    kills_at_10          TINYINT UNSIGNED  NULL COMMENT '10분 킬',
    deaths_at_10         TINYINT UNSIGNED  NULL COMMENT '10분 데스',
    assists_at_10        TINYINT UNSIGNED  NULL COMMENT '10분 어시스트',
    gold_at_15           INT UNSIGNED      NULL COMMENT '15분 골드',
    xp_at_15             INT UNSIGNED      NULL COMMENT '15분 경험치',
    cs_at_15             SMALLINT UNSIGNED NULL COMMENT '15분 CS',
    kills_at_15          TINYINT UNSIGNED  NULL COMMENT '15분 킬',
    deaths_at_15         TINYINT UNSIGNED  NULL COMMENT '15분 데스',
    assists_at_15        TINYINT UNSIGNED  NULL COMMENT '15분 어시스트',
    gold_at_20           INT UNSIGNED      NULL COMMENT '20분 골드',
    xp_at_20             INT UNSIGNED      NULL COMMENT '20분 경험치',
    cs_at_20             SMALLINT UNSIGNED NULL COMMENT '20분 CS',
    kills_at_20          TINYINT UNSIGNED  NULL COMMENT '20분 킬',
    deaths_at_20         TINYINT UNSIGNED  NULL COMMENT '20분 데스',
    assists_at_20        TINYINT UNSIGNED  NULL COMMENT '20분 어시스트',
    gold_at_25           INT UNSIGNED      NULL COMMENT '25분 골드',
    xp_at_25             INT UNSIGNED      NULL COMMENT '25분 경험치',
    cs_at_25             SMALLINT UNSIGNED NULL COMMENT '25분 CS',
    kills_at_25          TINYINT UNSIGNED  NULL COMMENT '25분 킬',
    deaths_at_25         TINYINT UNSIGNED  NULL COMMENT '25분 데스',
    assists_at_25        TINYINT UNSIGNED  NULL COMMENT '25분 어시스트',
    PRIMARY KEY (player_set_id),
    UNIQUE KEY uk_player_set_position (team_set_id, position),
    UNIQUE KEY uk_player_set_player (team_set_id, player_id),
    KEY ix_ps_player (player_id),
    KEY ix_ps_champion (champion_id),
    CONSTRAINT fk_player_set_team_set FOREIGN KEY (team_set_id) REFERENCES team_set (team_set_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_player_set_player FOREIGN KEY (player_id) REFERENCES player (player_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_player_set_champion FOREIGN KEY (champion_id) REFERENCES champion (champion_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_player_set_points CHECK (total_points <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '선수세트지표';

-- 9. 밴
CREATE TABLE ban (
    ban_id      BIGINT           NOT NULL AUTO_INCREMENT COMMENT '밴ID',
    team_set_id BIGINT           NOT NULL COMMENT '팀세트기록ID',
    ban_order   TINYINT UNSIGNED NOT NULL COMMENT '밴 순서 1~5',
    champion_id BIGINT           NOT NULL COMMENT '챔피언ID',
    PRIMARY KEY (ban_id),
    UNIQUE KEY uk_ban_order (team_set_id, ban_order),
    UNIQUE KEY uk_ban_champion (team_set_id, champion_id),
    CONSTRAINT fk_ban_team_set FOREIGN KEY (team_set_id) REFERENCES team_set (team_set_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ban_champion FOREIGN KEY (champion_id) REFERENCES champion (champion_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ban_order CHECK (ban_order BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '밴';

-- 10. 선수시즌지표
CREATE TABLE player_season (
    season_id        BIGINT            NOT NULL AUTO_INCREMENT COMMENT '선수시즌지표ID',
    player_id        BIGINT            NOT NULL COMMENT '선수ID',
    sets             SMALLINT UNSIGNED NOT NULL COMMENT '출전세트 수',
    total_points     TINYINT UNSIGNED  NULL COMMENT '총합 점수 0~100 (10세트 미만 NULL)',
    combat_points    TINYINT UNSIGNED  NULL COMMENT '전투 영역 점수',
    damage_points    TINYINT UNSIGNED  NULL COMMENT '딜 영역 점수',
    laning_points    TINYINT UNSIGNED  NULL COMMENT '라인전 영역 점수',
    growth_points    TINYINT UNSIGNED  NULL COMMENT '성장 영역 점수',
    ward_points      TINYINT UNSIGNED  NULL COMMENT '시야 영역 점수',
    survival_points  TINYINT UNSIGNED  NULL COMMENT '생존 영역 점수',
    rank_in_position TINYINT UNSIGNED  NULL COMMENT '포지션 내 순위',
    calculated_at    DATETIME          NOT NULL COMMENT '계산일시',
    PRIMARY KEY (season_id),
    UNIQUE KEY uk_season_player (player_id),
    KEY ix_season_total (total_points),
    CONSTRAINT fk_season_player FOREIGN KEY (player_id) REFERENCES player (player_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_season_points CHECK (total_points <= 100 AND combat_points <= 100 AND damage_points <= 100
        AND laning_points <= 100 AND growth_points <= 100 AND ward_points <= 100 AND survival_points <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '선수시즌지표';
