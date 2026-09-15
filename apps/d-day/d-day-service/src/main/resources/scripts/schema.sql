-- 이 파일은 docs/SCHEMA.md §12 에서 뽑아낸 것이다. 손으로 고치지 않는다.
-- 문서를 고치고 다시 뽑는다 — 둘이 갈라지면 어느 쪽이 진짜인지 알 수 없게 된다.
--
-- 적용:  psql "$SPRING_DATASOURCE_URL" -v ON_ERROR_STOP=1 -f schema.sql
-- 자동 적용하지 않는다 (SCHEMA.md §11.3) — 운영은 ddl-auto: validate 다.

-- =============================================================
-- d-day-service 스키마 — 빈 DB 를 세우는 스크립트 (변경 이력이 아니다)
-- 근거: docs/SCHEMA.md · SPEC.md §9/§11/§12 · ARCHITECTURE.md
-- =============================================================

-- -------------------------------------------------------------
-- country  (SPEC §9.2) — CLDR 시드. 동기화 대상이 아니다.
-- -------------------------------------------------------------
CREATE TABLE country (
    code              varchar(2)   PRIMARY KEY,
    name_en           varchar(100) NOT NULL,
    name_ko           varchar(100) NOT NULL,
    primary_zone_id   varchar(64)  NOT NULL,
    zone_is_ambiguous boolean      NOT NULL DEFAULT false,
    weekend_mask      smallint     NOT NULL,
    created_at        timestamp(6) NOT NULL,
    updated_at        timestamp(6) NOT NULL,
    CONSTRAINT ck_country_code    CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_country_weekend CHECK (weekend_mask BETWEEN 1 AND 127)
);
COMMENT ON COLUMN country.weekend_mask IS
  'ISO-8601 요일 비트마스크. bit0=월 … bit6=일. 토일=96, 금토=48, 일요일만=64';
COMMENT ON COLUMN country.zone_is_ambiguous IS
  'true 면 시간대 여럿 중 대표를 고른 것. 응답에 표시한다 (SPEC §9.9(4))';

-- -------------------------------------------------------------
-- holiday  (SPEC §9.3 · §11.3)
-- -------------------------------------------------------------
CREATE TABLE holiday (
    id               bigint        PRIMARY KEY,
    country_code     varchar(2)    NOT NULL REFERENCES country(code),
    holiday_date     date          NOT NULL,
    holiday_year     smallint      NOT NULL,
    name_en          varchar(200)  NOT NULL,
    name_local       varchar(200),
    name_slug        varchar(120)  NOT NULL,
    subdivision_key  varchar(1000) NOT NULL DEFAULT '',
    is_global        boolean       NOT NULL,
    is_fixed         boolean       NOT NULL,
    types_raw        varchar(200)  NOT NULL,
    is_public        boolean       NOT NULL,
    launch_year      smallint,
    last_seen_run_id bigint        NOT NULL,
    deleted_at       timestamptz,
    created_at       timestamp(6)  NOT NULL,
    updated_at       timestamp(6)  NOT NULL,

    CONSTRAINT ck_holiday_year   CHECK (holiday_year = EXTRACT(YEAR FROM holiday_date)::smallint),
    CONSTRAINT ck_holiday_types  CHECK (types_raw ~ '^[A-Za-z]+(,[A-Za-z]+)*$'),
    CONSTRAINT ck_holiday_public CHECK (is_public = (strpos(',' || types_raw || ',', ',Public,') > 0)),
    CONSTRAINT ck_holiday_global CHECK (NOT (is_global AND subdivision_key <> '')),
    CONSTRAINT ck_holiday_subdiv CHECK (
            subdivision_key !~ '[[:space:]]'
        AND subdivision_key !~ ',,'
        AND subdivision_key !~ '^,'
        AND subdivision_key !~ ',$'
    )
);
COMMENT ON COLUMN holiday.subdivision_key IS
  '지역 코드를 정렬해 쉼표로 이은 것. 전국이면 빈 문자열(NULL 아님 — 유일 제약이 무력해진다)';
COMMENT ON COLUMN holiday.types_raw IS
  'Nager types 배열을 정렬해 쉼표로 이은 것. Public 만 노출하되 전부 저장한다 (SPEC A-10)';

CREATE UNIQUE INDEX uk_holiday_natural
    ON holiday (country_code, holiday_date, name_en, subdivision_key);

CREATE INDEX ix_holiday_country_year
    ON holiday (country_code, holiday_year)
    INCLUDE (holiday_date, name_en, name_local, name_slug,
             subdivision_key, is_global, is_public, launch_year)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_holiday_on_date
    ON holiday (holiday_date)
    INCLUDE (country_code, name_slug, is_global)
    WHERE is_public AND deleted_at IS NULL;

CREATE INDEX ix_holiday_axis_name
    ON holiday (holiday_year, name_slug, country_code)
    WHERE is_public AND deleted_at IS NULL;

CREATE INDEX ix_holiday_axis_country
    ON holiday (holiday_year, is_global, country_code, holiday_date)
    WHERE is_public AND deleted_at IS NULL;

-- -------------------------------------------------------------
-- long_weekend  (SPEC §9.3 · §11.1)
-- -------------------------------------------------------------
CREATE TABLE long_weekend (
    id               bigint       PRIMARY KEY,
    country_code     varchar(2)   NOT NULL REFERENCES country(code),
    holiday_year     smallint     NOT NULL,
    start_date       date         NOT NULL,
    end_date         date         NOT NULL,
    need_bridge      boolean      NOT NULL,
    bridge_days      varchar(200) NOT NULL DEFAULT '',
    last_seen_run_id bigint       NOT NULL,
    deleted_at       timestamptz,
    created_at       timestamp(6) NOT NULL,
    updated_at       timestamp(6) NOT NULL,
    CONSTRAINT ck_lw_range CHECK (end_date >= start_date),
    CONSTRAINT ck_lw_year  CHECK (holiday_year = EXTRACT(YEAR FROM start_date)::smallint)
);
COMMENT ON COLUMN long_weekend.bridge_days IS
  '원천이 주는 값이다. 계산하지 않는다 (SPEC §9.3). dayCount 는 담지 않는다';

CREATE UNIQUE INDEX uk_long_weekend_natural
    ON long_weekend (country_code, start_date, end_date);

CREATE INDEX ix_long_weekend_country_year
    ON long_weekend (country_code, holiday_year)
    INCLUDE (start_date, end_date, need_bridge, bridge_days)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_long_weekend_rank
    ON long_weekend (holiday_year, country_code, start_date, end_date)
    WHERE deleted_at IS NULL;

-- -------------------------------------------------------------
-- holiday_name_label  (SPEC §9.9(1))
-- -------------------------------------------------------------
CREATE TABLE holiday_name_label (
    name_slug      varchar(120) NOT NULL,
    lang           varchar(5)   NOT NULL,
    label          varchar(200) NOT NULL,
    sample_name_en varchar(200) NOT NULL,
    created_at     timestamp(6) NOT NULL,
    updated_at     timestamp(6) NOT NULL,
    PRIMARY KEY (name_slug, lang)
);
COMMENT ON COLUMN holiday_name_label.sample_name_en IS
  '사람이 표를 채울 때 보는 참고값. 응답에 나가지 않는다';

-- -------------------------------------------------------------
-- holiday_coverage  (§8.4) — 정의역과 급감 가드의 근거
-- -------------------------------------------------------------
CREATE TABLE holiday_coverage (
    country_code         varchar(2) NOT NULL REFERENCES country(code),
    holiday_year         smallint   NOT NULL,
    last_ok_run_id       bigint,
    last_ok_at           timestamptz,
    source_count         integer    NOT NULL DEFAULT 0,
    stored_count         integer    NOT NULL DEFAULT 0,
    dropped_count        integer    NOT NULL DEFAULT 0,
    consecutive_failures smallint   NOT NULL DEFAULT 0,
    created_at           timestamp(6) NOT NULL,
    updated_at           timestamp(6) NOT NULL,
    PRIMARY KEY (country_code, holiday_year)
);

-- -------------------------------------------------------------
-- member_reference  (SPEC §9.6) — auth-service 가 원본
-- -------------------------------------------------------------
CREATE TABLE member_reference (
    member_id  bigint       PRIMARY KEY,
    email      varchar(255),
    nickname   varchar(100),
    zone_id    varchar(64),
    status     varchar(20)  NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE','WITHDRAWN'))
);

-- -------------------------------------------------------------
-- anniversary  (SPEC §9.6)
-- -------------------------------------------------------------
CREATE TABLE anniversary (
    id              bigint       PRIMARY KEY,
    member_id       bigint       NOT NULL,
    title           varchar(100) NOT NULL,
    anchor_date     date         NOT NULL,
    calendar        varchar(10)  NOT NULL,
    leap_policy     varchar(12)  NOT NULL,
    recurrence      varchar(10)  NOT NULL,
    count_direction varchar(10)  NOT NULL,
    zone_id         varchar(64)  NOT NULL,
    notify_offsets  varchar(50)  NOT NULL DEFAULT '',
    expanded_until  date,
    created_at      timestamp(6) NOT NULL,
    updated_at      timestamp(6) NOT NULL,
    CONSTRAINT ck_anniv_calendar CHECK (calendar IN ('SOLAR','LUNAR')),
    CONSTRAINT ck_anniv_leap     CHECK (leap_policy IN ('LEAP_ONLY','PLAIN_ONLY','EITHER')),
    CONSTRAINT ck_anniv_recur    CHECK (recurrence IN ('NONE','YEARLY')),
    CONSTRAINT ck_anniv_dir      CHECK (count_direction IN ('D_DAY','D_PLUS')),
    CONSTRAINT ck_anniv_offsets  CHECK (notify_offsets = '' OR notify_offsets ~ '^[0-9]+(,[0-9]+)*$'),
    CONSTRAINT ck_anniv_leap_dom CHECK (calendar = 'LUNAR' OR leap_policy = 'PLAIN_ONLY'),
    CONSTRAINT ck_anniv_expanded CHECK (recurrence = 'NONE' OR expanded_until IS NOT NULL)
);
COMMENT ON COLUMN anniversary.member_id IS
  'auth-service 소유. 논리 참조라 FK 를 걸지 않는다 (Waiting.restaurantId 와 같은 관례)';

CREATE INDEX ix_anniversary_member ON anniversary (member_id, id);
CREATE INDEX ix_anniversary_expand ON anniversary (expanded_until) WHERE recurrence = 'YEARLY';

-- -------------------------------------------------------------
-- anniversary_occurrence  (ARCHITECTURE §2.3) — 투영
-- -------------------------------------------------------------
CREATE TABLE anniversary_occurrence (
    anniversary_id  bigint       NOT NULL REFERENCES anniversary(id) ON DELETE CASCADE,
    occurrence_date date         NOT NULL,
    notify_offset   smallint     NOT NULL,
    member_id       bigint       NOT NULL,
    notified_at     timestamptz,
    created_at      timestamp(6) NOT NULL,
    PRIMARY KEY (anniversary_id, occurrence_date, notify_offset),
    CONSTRAINT ck_occ_offset CHECK (notify_offset BETWEEN 0 AND 365)
);
COMMENT ON TABLE anniversary_occurrence IS
  '저장하는 것은 발생일(절대 날짜)이지 D-day 가 아니다 (SPEC §9.9(2) 와 부딪히지 않는 까닭)';

CREATE INDEX ix_occurrence_due
    ON anniversary_occurrence (occurrence_date, notify_offset)
    INCLUDE (anniversary_id, member_id)
    WHERE notified_at IS NULL;

CREATE INDEX ix_occurrence_purge
    ON anniversary_occurrence (occurrence_date)
    WHERE notified_at IS NOT NULL;

ALTER TABLE anniversary_occurrence
    SET (autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.01);

-- -------------------------------------------------------------
-- release — league / team / sport_event  (SPEC §9.7 · §12)
-- -------------------------------------------------------------
CREATE TABLE league (
    id           bigint       PRIMARY KEY,
    source       varchar(20)  NOT NULL,
    external_id  varchar(40)  NOT NULL,
    sport        varchar(40)  NOT NULL,
    name         varchar(120) NOT NULL,
    country_code varchar(2)   REFERENCES country(code),
    zone_id      varchar(64)  NOT NULL,
    enabled      boolean      NOT NULL DEFAULT true,
    created_at   timestamp(6) NOT NULL,
    updated_at   timestamp(6) NOT NULL
);
CREATE UNIQUE INDEX uk_league_source ON league (source, external_id);

CREATE TABLE team (
    id          bigint       PRIMARY KEY,
    league_id   bigint       NOT NULL REFERENCES league(id),
    source      varchar(20)  NOT NULL,
    external_id varchar(40)  NOT NULL,
    name        varchar(120) NOT NULL,
    name_ko     varchar(120),
    is_stub     boolean      NOT NULL DEFAULT false,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6) NOT NULL
);
COMMENT ON COLUMN team.is_stub IS
  '모르는 팀 id 를 만났을 때 FK 를 지키려고 먼저 넣은 행. 0이 아니면 신호다 (§1.4)';
CREATE UNIQUE INDEX uk_team_source ON team (source, external_id);
CREATE INDEX ix_team_league ON team (league_id);

CREATE TABLE sport_event (
    id            bigint       PRIMARY KEY,
    source        varchar(20)  NOT NULL,
    external_id   varchar(40)  NOT NULL,
    league_id     bigint       NOT NULL REFERENCES league(id),
    home_team_id  bigint       REFERENCES team(id),
    away_team_id  bigint       REFERENCES team(id),
    name          varchar(200) NOT NULL,
    starts_at     timestamptz,
    time_is_unknown boolean    NOT NULL DEFAULT false,
    status        varchar(20),
    postponed     boolean      NOT NULL DEFAULT false,
    season        varchar(20),
    venue         varchar(200),
    source_hash   char(32)     NOT NULL,
    last_seen_at  timestamptz  NOT NULL,
    created_at    timestamp(6) NOT NULL,
    updated_at    timestamp(6) NOT NULL
);
COMMENT ON COLUMN sport_event.source_hash IS
  '가변 필드의 md5. 안 바뀌었으면 UPDATE 를 건너뛴다 — 창 누적이 10분마다 같은 행을 다시 보므로';
COMMENT ON COLUMN sport_event.time_is_unknown IS
  'strTime 이 없어 날짜만 확실할 때. 조용히 00:00 으로 퉁치지 않는다 (SPEC §9.9(4))';

CREATE UNIQUE INDEX uk_sport_event_source ON sport_event (source, external_id);
CREATE INDEX ix_sport_event_league_time ON sport_event (league_id, starts_at);
CREATE INDEX ix_sport_event_home        ON sport_event (home_team_id, starts_at);
CREATE INDEX ix_sport_event_away        ON sport_event (away_team_id, starts_at);

-- -------------------------------------------------------------
-- release — movie  (SPEC §9.7)
-- -------------------------------------------------------------
CREATE TABLE movie (
    id          bigint       PRIMARY KEY,
    source      varchar(20)  NOT NULL,
    external_id varchar(40)  NOT NULL,
    title_en    varchar(300),
    title_ko    varchar(300),
    source_hash char(32)     NOT NULL,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6) NOT NULL
);
CREATE UNIQUE INDEX uk_movie_source ON movie (source, external_id);

CREATE TABLE movie_release (
    movie_id      bigint       NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    country_code  varchar(2)   NOT NULL REFERENCES country(code),
    release_date  date         NOT NULL,
    certification varchar(20),
    created_at    timestamp(6) NOT NULL,
    updated_at    timestamp(6) NOT NULL,
    PRIMARY KEY (movie_id, country_code)
);
COMMENT ON TABLE movie_release IS
  '개봉일은 나라마다 다르다. 어느 나라 기준인지 없이는 D-day 가 성립하지 않는다 (SPEC §9.7)';
CREATE INDEX ix_movie_release_upcoming
    ON movie_release (country_code, release_date) INCLUDE (movie_id);

-- -------------------------------------------------------------
-- release — watch / date_change
-- -------------------------------------------------------------
CREATE TABLE watch (
    id           bigint       PRIMARY KEY,
    member_id    bigint       NOT NULL,
    subject_type varchar(20)  NOT NULL,
    subject_id   bigint       NOT NULL,
    created_at   timestamp(6) NOT NULL,
    updated_at   timestamp(6) NOT NULL,
    CONSTRAINT ck_watch_subject CHECK (subject_type IN ('SPORT_EVENT','TEAM','MOVIE'))
);
CREATE UNIQUE INDEX uk_watch        ON watch (member_id, subject_type, subject_id);
CREATE INDEX        ix_watch_fanout ON watch (subject_type, subject_id) INCLUDE (member_id);

CREATE TABLE date_change (
    id           bigint       PRIMARY KEY,
    subject_type varchar(20)  NOT NULL,
    subject_id   bigint       NOT NULL,
    field        varchar(30)  NOT NULL,
    old_value    varchar(64),
    new_value    varchar(64),
    detected_at  timestamptz  NOT NULL,
    sync_run_id  bigint,
    created_at   timestamp(6) NOT NULL,
    updated_at   timestamp(6) NOT NULL,
    CONSTRAINT ck_dc_subject CHECK (subject_type IN ('SPORT_EVENT','MOVIE_RELEASE')),
    CONSTRAINT ck_dc_field   CHECK (field IN ('STARTS_AT','RELEASE_DATE','POSTPONED'))
);
COMMENT ON TABLE date_change IS
  '유일 제약을 걸지 않는다. 중복 제거는 outbox_event.idempotency_key 의 일이다 (§6.3)';
CREATE INDEX ix_date_change_subject  ON date_change (subject_type, subject_id, detected_at DESC);
CREATE INDEX ix_date_change_detected ON date_change (detected_at);

-- -------------------------------------------------------------
-- outbox_event  (ARCHITECTURE §3.2 · §3.3 · §3.5) — 한 테이블
-- -------------------------------------------------------------
CREATE TABLE outbox_event (
    id              bigint       PRIMARY KEY,
    aggregate_type  varchar(30)  NOT NULL,
    aggregate_id    varchar(64)  NOT NULL,
    event_type      varchar(50)  NOT NULL,
    partition_key   varchar(64)  NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    payload         text         NOT NULL,
    status          varchar(20)  NOT NULL,
    retry_count     smallint     NOT NULL DEFAULT 0,
    published_at    timestamptz,
    created_at      timestamp(6) NOT NULL,
    updated_at      timestamp(6) NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','SENDING','PUBLISHED','FAILED'))
);
COMMENT ON COLUMN outbox_event.partition_key IS
  'Kafka 파티션 키. aggregate_id 와 다르다 — notification 은 memberId, release 는 경기 id';

CREATE UNIQUE INDEX uk_outbox_idem  ON outbox_event (aggregate_type, idempotency_key);
CREATE INDEX ix_outbox_claim ON outbox_event (created_at) WHERE status IN ('PENDING','SENDING');
CREATE INDEX ix_outbox_purge ON outbox_event (published_at) WHERE status = 'PUBLISHED';

ALTER TABLE outbox_event
    SET (autovacuum_vacuum_scale_factor = 0.01, autovacuum_vacuum_cost_delay = 0);

-- -------------------------------------------------------------
-- sync_run / sync_run_item  (ARCHITECTURE §5.2 · §5.3 · §4.3)
-- -------------------------------------------------------------
CREATE TABLE sync_run (
    id             bigint       PRIMARY KEY,
    target         varchar(20)  NOT NULL,
    trigger_source varchar(10)  NOT NULL,
    status         varchar(12)  NOT NULL,
    started_at     timestamptz  NOT NULL,
    finished_at    timestamptz,
    item_total     integer      NOT NULL DEFAULT 0,
    item_ok        integer      NOT NULL DEFAULT 0,
    item_failed    integer      NOT NULL DEFAULT 0,
    item_aborted   integer      NOT NULL DEFAULT 0,
    dropped_total  integer      NOT NULL DEFAULT 0,
    cache_version  bigint,
    warm_status    varchar(12),
    created_at     timestamp(6) NOT NULL,
    updated_at     timestamp(6) NOT NULL,
    CONSTRAINT ck_run_target  CHECK (target IN ('HOLIDAY','SPORT_EVENT','MOVIE')),
    CONSTRAINT ck_run_trigger CHECK (trigger_source IN ('SCHEDULE','ADMIN')),
    CONSTRAINT ck_run_status  CHECK (status IN ('RUNNING','SUCCEEDED','PARTIAL','FAILED')),
    CONSTRAINT ck_run_warm    CHECK (warm_status IS NULL
                                     OR warm_status IN ('SKIPPED','OK','FAILED'))
);
COMMENT ON COLUMN sync_run.warm_status IS
  '워밍 실패는 플립을 막고 낡은 자료가 계속 나가게 한다 — 이 구조의 유일한 고장 모드라 여기 남긴다 (ARCHITECTURE §4.3)';
CREATE INDEX ix_sync_run_target_time ON sync_run (target, started_at DESC);

CREATE TABLE sync_run_item (
    id            bigint       PRIMARY KEY,
    sync_run_id   bigint       NOT NULL REFERENCES sync_run(id) ON DELETE CASCADE,
    target        varchar(20)  NOT NULL,
    country_code  varchar(2),
    holiday_year  smallint,
    league_id     bigint,
    status        varchar(12)  NOT NULL,
    source_count  integer,
    stored_count  integer,
    dropped_count integer      NOT NULL DEFAULT 0,
    error_code    varchar(60),
    error_message varchar(500),
    duration_ms   integer,
    created_at    timestamp(6) NOT NULL,
    CONSTRAINT ck_item_status CHECK (status IN ('OK','FAILED','SKIPPED','ABORTED'))
);
COMMENT ON COLUMN sync_run_item.dropped_count IS
  '우리 코퍼스에 안 걸린 황금연휴 등. 평소 0이어야 정상이고 0이 아니면 신호다 (SPEC §11.4)';
CREATE INDEX ix_sync_run_item_run ON sync_run_item (sync_run_id);
CREATE INDEX ix_sync_run_item_bad
    ON sync_run_item (target, country_code, holiday_year)
    WHERE status IN ('FAILED','ABORTED');

-- -------------------------------------------------------------
-- schema_change  (§14) — 이력. **도메인 표가 아니다.**
--
-- 마이그레이션 도구를 안 쓰기로 했으므로(§11.2) 「무엇이 적용됐는가」를 들고 있는
-- 자리가 없었다. 그 자리를 DB 안에 만든다 — 파일 밖에 두면 인스턴스마다 다른
-- 답을 갖게 되고, 그것은 답이 없는 것보다 나쁘다.
-- -------------------------------------------------------------
CREATE TABLE schema_change (
    revision   integer      PRIMARY KEY,
    name       varchar(80)  NOT NULL,
    applied_at timestamptz  NOT NULL DEFAULT now(),
    applied_by varchar(80)  NOT NULL DEFAULT current_user
);
COMMENT ON TABLE schema_change IS
  '적용된 스키마 변경. 빈 DB 는 기준선 한 줄만 갖는다 (docs/SCHEMA.md §14)';

-- 이 스크립트가 세우는 리비전. **파일의 맨 마지막 문장이어야 한다** — 위의 것이
-- 전부 섰다는 뜻이기 때문이다. 변경 스크립트를 더할 때마다 이 숫자도 같이 올린다 (§14 R1).
INSERT INTO schema_change (revision, name) VALUES (1, 'baseline — scripts/schema.sql');
