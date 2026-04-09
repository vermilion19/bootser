package com.booster.queryburst.order.application.dto;

/**
 * 상품별 판매 통계 결과.
 *
 * 쿼리 실습: ORDER_ITEM + PRODUCT JOIN + GROUP BY product_id
 * 커버링 인덱스: idx_order_item_covering (product_id, quantity, unit_price)
 *   → 테이블 접근 없이 인덱스만으로 quantity/unit_price 합산 가능
 */
public record ProductSalesResult(
        Long productId,
        String productName,
        Long totalQuantity,
        Long totalRevenue
) {
}
