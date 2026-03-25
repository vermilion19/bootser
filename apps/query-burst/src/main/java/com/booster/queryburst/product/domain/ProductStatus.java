package com.booster.queryburst.product.domain;

/**
 * 상품 상태
 * - ACTIVE가 대부분 (약 70%) → 부분 인덱스(Partial Index) 실습 적합
 *   예) CREATE INDEX idx_product_active ON product(id) WHERE status = 'ACTIVE'
 */
public enum ProductStatus {
    ACTIVE,    // 판매 중 (70%)
    INACTIVE,  // 판매 중단 (20%)
    SOLD_OUT   // 품절 (10%)
}
