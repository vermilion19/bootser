-- ============================================================
-- V2: product 테이블에 펜싱 토큰 컬럼 추가
--
-- 분산 락의 펜싱 토큰을 DB 레벨에서 검증하기 위해 사용.
-- 재고 차감 시 last_fence_token < 요청 토큰인 경우에만 처리.
-- ============================================================

ALTER TABLE product
    ADD COLUMN last_fence_token BIGINT NOT NULL DEFAULT 0;
