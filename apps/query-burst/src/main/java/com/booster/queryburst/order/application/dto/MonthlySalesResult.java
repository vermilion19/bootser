package com.booster.queryburst.order.application.dto;

/**
 * 월별 매출 집계 결과.
 *
 * 쿼리 실습: DATE_TRUNC('month', ordered_at) + GROUP BY
 * 인덱스: idx_orders_status_ordered_at (status, ordered_at) — 배송완료 상태 필터 + 기간 조회
 */
public record MonthlySalesResult(
        int year,
        int month,
        Long orderCount,
        Long totalRevenue
) {
}
