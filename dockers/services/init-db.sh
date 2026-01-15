#!/bin/bash
set -e

# ==============================================
# Booster Database Initialization Script
# Creates multiple databases and their tables
# ==============================================

POSTGRES_USER="${POSTGRES_USER:-booster}"

echo "=========================================="
echo "Creating databases..."
echo "=========================================="

# 데이터베이스 생성
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE auth_service_db;
    CREATE DATABASE restaurant_service_db;
    CREATE DATABASE waiting_service_db;
    CREATE DATABASE noti_service_db;
    CREATE DATABASE promotion_service_db;
EOSQL

echo "Databases created successfully!"

# ==============================================
# AUTH SERVICE - auth_service_db
# ==============================================
echo "Initializing auth_service_db..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "auth_service_db" <<-EOSQL

    CREATE TABLE IF NOT EXISTS users (
        id                  BIGINT          PRIMARY KEY,
        username            VARCHAR(50)     NOT NULL UNIQUE,
        password            VARCHAR(255)    NOT NULL,
        role                VARCHAR(20)     NOT NULL,
        created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
        updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
    );

    COMMENT ON TABLE users IS '사용자 정보';
    COMMENT ON COLUMN users.role IS 'USER, PARTNER, ADMIN';

EOSQL

# ==============================================
# RESTAURANT SERVICE - restaurant_service_db
# ==============================================
echo "Initializing restaurant_service_db..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "restaurant_service_db" <<-EOSQL

    CREATE TABLE IF NOT EXISTS restaurant (
        id                  BIGSERIAL       PRIMARY KEY,
        name                VARCHAR(255)    NOT NULL,
        capacity            INT             NOT NULL,
        current_occupancy   INT             NOT NULL DEFAULT 0,
        max_waiting_limit   INT             NOT NULL,
        status              VARCHAR(20)     NOT NULL,
        created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
        updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
    );

    COMMENT ON TABLE restaurant IS '식당 정보';
    COMMENT ON COLUMN restaurant.status IS 'OPEN, WAITING_CLOSED, CLOSED';

    -- Outbox 테이블
    CREATE TABLE IF NOT EXISTS outbox_events (
        id                  BIGINT          PRIMARY KEY,
        aggregate_type      VARCHAR(100),
        aggregate_id        BIGINT,
        event_type          VARCHAR(100),
        payload             TEXT,
        published           BOOLEAN         DEFAULT FALSE,
        created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
    );

    CREATE INDEX IF NOT EXISTS idx_outbox_published
        ON outbox_events(published) WHERE published = FALSE;

EOSQL

# ==============================================
# WAITING SERVICE - waiting_service_db
# ==============================================
echo "Initializing waiting_service_db..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "waiting_service_db" <<-EOSQL

    CREATE TABLE IF NOT EXISTS waiting (
        id                  BIGINT          PRIMARY KEY,
        restaurant_id       BIGINT          NOT NULL,
        guest_phone         VARCHAR(20)     NOT NULL,
        party_size          INT             NOT NULL,
        waiting_number      INT             NOT NULL,
        status              VARCHAR(20)     NOT NULL,
        created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
        updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
    );

    COMMENT ON TABLE waiting IS '대기 정보';
    COMMENT ON COLUMN waiting.status IS 'WAITING, CALLED, ENTERED, CANCELED';

    CREATE INDEX IF NOT EXISTS idx_waiting_restaurant_id ON waiting(restaurant_id);
    CREATE INDEX IF NOT EXISTS idx_waiting_status ON waiting(status);
    CREATE INDEX IF NOT EXISTS idx_waiting_guest_phone ON waiting(guest_phone);

    -- Outbox 테이블
    CREATE TABLE IF NOT EXISTS outbox_events (
        id                  BIGINT          PRIMARY KEY,
        aggregate_type      VARCHAR(100),
        aggregate_id        BIGINT,
        event_type          VARCHAR(100),
        payload             TEXT,
        published           BOOLEAN         DEFAULT FALSE,
        created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
    );

    CREATE INDEX IF NOT EXISTS idx_outbox_published
        ON outbox_events(published) WHERE published = FALSE;

EOSQL

# ==============================================
# NOTIFICATION SERVICE - noti_service_db
# ==============================================
echo "Initializing noti_service_db..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "noti_service_db" <<-EOSQL

    CREATE TABLE IF NOT EXISTS notification_logs (
        id                  BIGINT          PRIMARY KEY,
        restaurant_id       BIGINT,
        waiting_id          BIGINT,
        target              VARCHAR(255),
        message             TEXT,
        status              VARCHAR(20),
        sent_at             TIMESTAMP,
        created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
    );

    COMMENT ON TABLE notification_logs IS '알림 발송 로그';
    COMMENT ON COLUMN notification_logs.status IS 'PENDING, SENT, FAILED';

    CREATE INDEX IF NOT EXISTS idx_notification_restaurant_id ON notification_logs(restaurant_id);
    CREATE INDEX IF NOT EXISTS idx_notification_waiting_id ON notification_logs(waiting_id);
    CREATE INDEX IF NOT EXISTS idx_notification_status ON notification_logs(status);

EOSQL

# ==============================================
# PROMOTION SERVICE - promotion_service_db
# ==============================================
echo "Initializing promotion_service_db..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "promotion_service_db" <<-EOSQL

    CREATE TABLE IF NOT EXISTS coupon (
        id                  BIGINT          PRIMARY KEY,
        title               VARCHAR(255)    NOT NULL,
        total_quantity      INT             NOT NULL,
        issued_quantity     INT             NOT NULL DEFAULT 0,
        discount_amount     INT             NOT NULL,
        start_at            TIMESTAMP       NOT NULL,
        end_at              TIMESTAMP       NOT NULL,
        created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
        updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
    );

    COMMENT ON TABLE coupon IS '쿠폰 정책';

    CREATE TABLE IF NOT EXISTS coupon_issue (
        id                  BIGINT          PRIMARY KEY,
        coupon_id           BIGINT          NOT NULL,
        user_id             BIGINT          NOT NULL,
        status              VARCHAR(20)     NOT NULL,
        issued_at           TIMESTAMP       NOT NULL,
        used_at             TIMESTAMP,
        CONSTRAINT uk_coupon_user UNIQUE (coupon_id, user_id)
    );

    COMMENT ON TABLE coupon_issue IS '쿠폰 발급 내역';
    COMMENT ON COLUMN coupon_issue.status IS 'ISSUED, USED, EXPIRED';

    CREATE INDEX IF NOT EXISTS idx_coupon_issue_user_id ON coupon_issue(user_id);
    CREATE INDEX IF NOT EXISTS idx_coupon_issue_coupon_id ON coupon_issue(coupon_id);

EOSQL

echo "=========================================="
echo "All databases initialized successfully!"
echo "=========================================="
echo "Databases: auth_service_db, restaurant_service_db, waiting_service_db, noti_service_db, promotion_service_db"