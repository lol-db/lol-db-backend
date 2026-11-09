-- RIFT DATA 테이블 DDL (MySQL 8.0, InnoDB, utf8mb4_unicode_ci)
--
-- 재호의 「물리DB 설계」 테이블 기술서를 그대로 옮겼다.
-- 원본 DDL(RIFT_DATA_DDL_최종.sql)을 받으면 이 파일을 그것으로 바꾼다.
-- 뷰 4개(v_match_score, v_standings, v_team_set, v_player_set)와 스테이지 초기 데이터는 아직 없다.
--
-- 빈 데이터베이스에 한 번만 실행한다:
--   mysql -h 127.0.0.1 -u root -p riftdata < src/main/resources/db/schema.sql

SET NAMES utf8mb4;

-- 1. 팀
CREATE TABLE team (
    team_id        INT         NOT NULL AUTO_INCREMENT COMMENT '팀ID',
    team_code      VARCHAR(5)  NOT NULL COMMENT '팀 약어',
    team_name      VARCHAR(50) NOT NULL COMMENT '팀명 (CSV teamname)',
    source_team_id VARCHAR(60) NOT NULL COMMENT 'CSV teamid',
    PRIMARY KEY (team_id),
    UNIQUE KEY uk_team_code (team_code),
    UNIQUE KEY uk_team_source (source_team_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '팀';

-- 2. 선수
CREATE TABLE player (
    player_id        INT         NOT NULL AUTO_INCREMENT COMMENT '선수ID',
    team_id          INT         NOT NULL COMMENT '현재 소속 팀ID',
    player_name      VARCHAR(30) NOT NULL COMMENT '닉네임 (CSV playername)',
    main_position    CHAR(3)     NOT NULL COMMENT '주 포지션',
    source_player_id VARCHAR(60) NOT NULL COMMENT 'CSV playerid',
    PRIMARY KEY (player_id),
    UNIQUE KEY uk_player_source (source_player_id),
    KEY ix_player_name (player_name),
    KEY ix_player_pos (main_position),
    CONSTRAINT fk_player_team FOREIGN KEY (team_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_player_position CHECK (main_position IN ('TOP', 'JGL', 'MID', 'BOT', 'SUP'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '선수';

-- 3. 챔피언
CREATE TABLE champion (
    champion_id   INT         NOT NULL AUTO_INCREMENT COMMENT '챔피언ID',
    champion_name VARCHAR(30) NOT NULL COMMENT '챔피언 이름 (CSV champion, 영문)',
    PRIMARY KEY (champion_id),
    UNIQUE KEY uk_champion_name (champion_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '챔피언';

-- 4. 스테이지
CREATE TABLE stage (
    stage_code  VARCHAR(3)  NOT NULL COMMENT '스테이지 코드',
    stage_name  VARCHAR(30) NOT NULL COMMENT '스테이지명',
    stage_order TINYINT     NOT NULL COMMENT '진행 순서',
    start_date  DATE        NOT NULL COMMENT '시작일',
    end_date    DATE        NOT NULL COMMENT '종료일',
    is_regular  TINYINT(1)  NOT NULL COMMENT '순위표 집계 대상 여부 (정규 라운드 = 1)',
    PRIMARY KEY (stage_code),
    UNIQUE KEY uk_stage_order (stage_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '스테이지';

-- 5. 경기 (MATCH는 예약어라 lck_match)
CREATE TABLE lck_match (
    match_id   INT        NOT NULL AUTO_INCREMENT COMMENT '경기ID',
    stage_code VARCHAR(3) NOT NULL COMMENT '스테이지 코드',
    match_at   DATETIME   NOT NULL COMMENT '경기 일시 (CSV date)',
    best_of    TINYINT    NOT NULL DEFAULT 3 COMMENT '세트 수 (Bo)',
    team1_id   INT        NOT NULL COMMENT '팀1 ID',
    team2_id   INT        NOT NULL COMMENT '팀2 ID',
    PRIMARY KEY (match_id),
    KEY ix_match_at (match_at),
    CONSTRAINT fk_match_stage FOREIGN KEY (stage_code) REFERENCES stage (stage_code)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_match_team1 FOREIGN KEY (team1_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_match_team2 FOREIGN KEY (team2_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '경기';

-- 6. 세트
CREATE TABLE match_set (
    match_id       INT               NOT NULL COMMENT '경기ID',
    set_no         TINYINT           NOT NULL COMMENT '세트 번호 1–5 (CSV game)',
    source_game_id VARCHAR(40)       NOT NULL COMMENT 'CSV gameid',
    patch          VARCHAR(10)       NOT NULL COMMENT '패치 버전',
    duration_sec   SMALLINT UNSIGNED NOT NULL COMMENT '경기 시간(초) (CSV gamelength)',
    PRIMARY KEY (match_id, set_no),
    UNIQUE KEY uk_set_source (source_game_id),
    KEY ix_set_patch (patch),
    CONSTRAINT fk_set_match FOREIGN KEY (match_id) REFERENCES lck_match (match_id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '세트';

-- 7. 팀세트기록
CREATE TABLE team_set_stat (
    match_id              INT        NOT NULL COMMENT '경기ID',
    set_no                TINYINT    NOT NULL COMMENT '세트 번호',
    team_id               INT        NOT NULL COMMENT '팀ID',
    side                  CHAR(4)    NOT NULL COMMENT '진영 BLUE/RED',
    is_win                TINYINT(1) NOT NULL COMMENT '승리 여부',
    first_blood           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '첫 킬',
    first_dragon          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '첫 드래곤',
    dragons               TINYINT    NOT NULL DEFAULT 0 COMMENT '드래곤 전체',
    infernals             TINYINT    NOT NULL DEFAULT 0 COMMENT '화염 드래곤',
    mountains             TINYINT    NOT NULL DEFAULT 0 COMMENT '대지 드래곤',
    clouds                TINYINT    NOT NULL DEFAULT 0 COMMENT '바람 드래곤',
    oceans                TINYINT    NOT NULL DEFAULT 0 COMMENT '바다 드래곤',
    chemtechs             TINYINT    NOT NULL DEFAULT 0 COMMENT '화학공학 드래곤',
    hextechs              TINYINT    NOT NULL DEFAULT 0 COMMENT '마법공학 드래곤',
    elders                TINYINT    NOT NULL DEFAULT 0 COMMENT '장로 드래곤',
    first_herald          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '첫 전령',
    heralds               TINYINT    NOT NULL DEFAULT 0 COMMENT '전령',
    void_grubs            TINYINT    NOT NULL DEFAULT 0 COMMENT '공허 유충',
    atakhans              TINYINT    NOT NULL DEFAULT 0 COMMENT '아타칸',
    first_baron           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '첫 바론',
    barons                TINYINT    NOT NULL DEFAULT 0 COMMENT '바론',
    first_tower           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '첫 타워',
    first_mid_tower       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '첫 미드 타워',
    first_to_three_towers TINYINT(1) NOT NULL DEFAULT 0 COMMENT '타워 3개 먼저',
    towers                TINYINT    NOT NULL DEFAULT 0 COMMENT '파괴한 타워',
    turret_plates         TINYINT    NOT NULL DEFAULT 0 COMMENT '포탑 방패',
    inhibitors            TINYINT    NOT NULL DEFAULT 0 COMMENT '억제기',
    PRIMARY KEY (match_id, set_no, team_id),
    UNIQUE KEY uk_tss_side (match_id, set_no, side),
    CONSTRAINT fk_tss_set FOREIGN KEY (match_id, set_no) REFERENCES match_set (match_id, set_no)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_tss_team FOREIGN KEY (team_id) REFERENCES team (team_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_tss_side CHECK (side IN ('BLUE', 'RED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '팀세트기록';

-- 8. 선수세트기록
CREATE TABLE player_set_stat (
    match_id             INT               NOT NULL COMMENT '경기ID',
    set_no               TINYINT           NOT NULL COMMENT '세트 번호',
    team_id              INT               NOT NULL COMMENT '팀ID',
    position             CHAR(3)           NOT NULL COMMENT '포지션',
    player_id            INT               NOT NULL COMMENT '선수ID',
    champion_id          INT               NOT NULL COMMENT '챔피언ID',
    kills                TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '킬',
    deaths               TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '데스',
    assists              TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '어시스트',
    double_kills         TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '더블킬',
    triple_kills         TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '트리플킬',
    quadra_kills         TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '쿼드라킬',
    penta_kills          TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '펜타킬',
    first_blood_kill     TINYINT(1)        NOT NULL DEFAULT 0 COMMENT '첫 킬을 함',
    first_blood_assist   TINYINT(1)        NOT NULL DEFAULT 0 COMMENT '첫 킬 어시스트',
    first_blood_victim   TINYINT(1)        NOT NULL DEFAULT 0 COMMENT '첫 킬을 당함',
    damage_to_champions  INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '챔피언에게 준 피해',
    damage_taken_pm      DECIMAL(7, 1)     NULL COMMENT '분당 받은 피해',
    damage_mitigated_pm  DECIMAL(7, 1)     NULL COMMENT '분당 경감한 피해',
    damage_to_buildings  INT UNSIGNED      NULL COMMENT '건물에 준 피해',
    vision_score         SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '시야 점수',
    wards_placed         SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '와드 설치',
    wards_killed         SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '와드 제거',
    control_wards_bought SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '제어 와드 구매',
    total_gold           INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '총 골드',
    earned_gold          INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '획득 골드',
    gold_spent           INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '사용 골드',
    minion_kills         SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '미니언 처치',
    monster_kills        SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '정글 몬스터 처치',
    monster_kills_own    SMALLINT UNSIGNED NULL COMMENT '우리 정글 몬스터 처치',
    monster_kills_enemy  SMALLINT UNSIGNED NULL COMMENT '상대 정글 몬스터 처치',
    gold_at_10           INT UNSIGNED      NULL COMMENT '10분 골드',
    xp_at_10             INT UNSIGNED      NULL COMMENT '10분 경험치',
    cs_at_10             SMALLINT UNSIGNED NULL COMMENT '10분 CS',
    kills_at_10          TINYINT UNSIGNED  NULL COMMENT '10분 킬',
    assists_at_10        TINYINT UNSIGNED  NULL COMMENT '10분 어시스트',
    deaths_at_10         TINYINT UNSIGNED  NULL COMMENT '10분 데스',
    gold_at_15           INT UNSIGNED      NULL COMMENT '15분 골드',
    xp_at_15             INT UNSIGNED      NULL COMMENT '15분 경험치',
    cs_at_15             SMALLINT UNSIGNED NULL COMMENT '15분 CS',
    kills_at_15          TINYINT UNSIGNED  NULL COMMENT '15분 킬',
    assists_at_15        TINYINT UNSIGNED  NULL COMMENT '15분 어시스트',
    deaths_at_15         TINYINT UNSIGNED  NULL COMMENT '15분 데스',
    gold_at_20           INT UNSIGNED      NULL COMMENT '20분 골드',
    xp_at_20             INT UNSIGNED      NULL COMMENT '20분 경험치',
    cs_at_20             SMALLINT UNSIGNED NULL COMMENT '20분 CS',
    kills_at_20          TINYINT UNSIGNED  NULL COMMENT '20분 킬',
    assists_at_20        TINYINT UNSIGNED  NULL COMMENT '20분 어시스트',
    deaths_at_20         TINYINT UNSIGNED  NULL COMMENT '20분 데스',
    gold_at_25           INT UNSIGNED      NULL COMMENT '25분 골드',
    xp_at_25             INT UNSIGNED      NULL COMMENT '25분 경험치',
    cs_at_25             SMALLINT UNSIGNED NULL COMMENT '25분 CS',
    kills_at_25          TINYINT UNSIGNED  NULL COMMENT '25분 킬',
    assists_at_25        TINYINT UNSIGNED  NULL COMMENT '25분 어시스트',
    deaths_at_25         TINYINT UNSIGNED  NULL COMMENT '25분 데스',
    rating               DECIMAL(3, 1)     NULL COMMENT '세트 평점 0–10 (유도)',
    is_pog               TINYINT(1)        NOT NULL DEFAULT 0 COMMENT 'POG 여부 (유도)',
    PRIMARY KEY (match_id, set_no, team_id, position),
    UNIQUE KEY uk_pss_player (match_id, set_no, player_id),
    UNIQUE KEY uk_pss_champion (match_id, set_no, champion_id),
    KEY ix_pss_player (player_id),
    KEY ix_pss_champion (champion_id),
    KEY ix_pss_pos (match_id, set_no, position),
    CONSTRAINT fk_pss_tss FOREIGN KEY (match_id, set_no, team_id)
        REFERENCES team_set_stat (match_id, set_no, team_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_pss_player FOREIGN KEY (player_id) REFERENCES player (player_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pss_champion FOREIGN KEY (champion_id) REFERENCES champion (champion_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_pss_position CHECK (position IN ('TOP', 'JGL', 'MID', 'BOT', 'SUP'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '선수세트기록';

-- 9. 밴
CREATE TABLE set_ban (
    match_id    INT     NOT NULL COMMENT '경기ID',
    set_no      TINYINT NOT NULL COMMENT '세트 번호',
    team_id     INT     NOT NULL COMMENT '밴한 팀ID',
    ban_order   TINYINT NOT NULL COMMENT '밴 순서 1–5',
    champion_id INT     NOT NULL COMMENT '밴한 챔피언ID',
    PRIMARY KEY (match_id, set_no, team_id, ban_order),
    UNIQUE KEY uk_ban_champion (match_id, set_no, champion_id),
    CONSTRAINT fk_ban_tss FOREIGN KEY (match_id, set_no, team_id)
        REFERENCES team_set_stat (match_id, set_no, team_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ban_champion FOREIGN KEY (champion_id) REFERENCES champion (champion_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '밴';

-- 10. 선수시즌지표
CREATE TABLE player_season_stat (
    player_id      INT          NOT NULL COMMENT '선수ID',
    set_count      SMALLINT     NOT NULL COMMENT '출전 세트 수',
    overall_score  DECIMAL(5, 2) NULL COMMENT '종합 점수 0–100 (10세트 미만 NULL)',
    combat_score   DECIMAL(5, 2) NULL COMMENT '전투 영역 점수',
    damage_score   DECIMAL(5, 2) NULL COMMENT '딜 영역 점수',
    lane_score     DECIMAL(5, 2) NULL COMMENT '라인전 영역 점수',
    growth_score   DECIMAL(5, 2) NULL COMMENT '성장 영역 점수',
    vision_score   DECIMAL(5, 2) NULL COMMENT '시야 영역 점수',
    survival_score DECIMAL(5, 2) NULL COMMENT '생존 영역 점수',
    position_rank  SMALLINT     NULL COMMENT '포지션 내 순위',
    calculated_at  DATETIME     NOT NULL COMMENT '계산 일시',
    PRIMARY KEY (player_id),
    KEY ix_pss2_score (overall_score),
    CONSTRAINT fk_season_player FOREIGN KEY (player_id) REFERENCES player (player_id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '선수시즌지표';
