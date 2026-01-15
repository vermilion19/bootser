-- 1. 쿠폰 정책 테이블
CREATE TABLE coupon (
                        id BIGINT PRIMARY KEY,                 -- Snowflake ID
                        title VARCHAR(100) NOT NULL,           -- 쿠폰명 (예: 오픈기념 50% 할인)
                        total_quantity INT NOT NULL,           -- 전체 발행 수량
                        issued_quantity INT DEFAULT 0,         -- 현재 발급된 수량 (통계용)
                        discount_amount INT NOT NULL,          -- 할인 금액/율
                        start_at DATETIME NOT NULL,            -- 발급 시작 일시
                        end_at DATETIME NOT NULL,              -- 발급/사용 종료 일시
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 쿠폰 발급 내역 테이블
CREATE TABLE coupon_issue (
                              id BIGINT PRIMARY KEY,                 -- Snowflake ID
                              coupon_id BIGINT NOT NULL,             -- 어떤 쿠폰인지
                              user_id BIGINT NOT NULL,               -- 누가 받았는지
                              status VARCHAR(20) NOT NULL,           -- 상태 (ISSUED, USED, EXPIRED)
                              issued_at DATETIME NOT NULL,           -- 발급 시각
                              used_at DATETIME,                      -- 사용 시각 (NULL이면 미사용)

    -- 조회 성능 및 정합성을 위한 인덱스
                              CONSTRAINT uk_coupon_user UNIQUE (coupon_id, user_id), -- DB 레벨 중복 방지 (최후의 보루)
                              INDEX idx_user_id (user_id)                            -- "내 쿠폰함 조회" 성능 최적화
);