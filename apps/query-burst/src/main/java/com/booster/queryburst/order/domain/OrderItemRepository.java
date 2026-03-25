package com.booster.queryburst.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // 주문별 항목 조회 (인덱스: order_id)
    List<OrderItem> findByOrderId(Long orderId);

    // 상품별 판매 내역 (인덱스: product_id)
    List<OrderItem> findByProductId(Long productId);

    // 3중 JOIN: 카테고리별 총 매출 집계
    // OrderItem → Product → Category
    @Query("""
            SELECT c.name, SUM(oi.quantity * oi.unitPrice), COUNT(oi)
            FROM OrderItem oi
            JOIN oi.product p
            JOIN p.category c
            WHERE oi.createdAt >= :from
            GROUP BY c.name
            ORDER BY SUM(oi.quantity * oi.unitPrice) DESC
            """)
    List<Object[]> revenueByCategory(@Param("from") LocalDateTime from);

    // 4중 JOIN: 회원 등급별 카테고리별 구매 금액
    // OrderItem → Orders → Member + OrderItem → Product → Category
    @Query("""
            SELECT m.grade, c.name, SUM(oi.quantity * oi.unitPrice)
            FROM OrderItem oi
            JOIN oi.order o
            JOIN o.member m
            JOIN oi.product p
            JOIN p.category c
            WHERE o.status = 'DELIVERED'
            GROUP BY m.grade, c.name
            ORDER BY m.grade, SUM(oi.quantity * oi.unitPrice) DESC
            """)
    List<Object[]> revenueByMemberGradeAndCategory();

    // 인기 상품 TOP N (판매량 기준)
    @Query("""
            SELECT p.id, p.name, SUM(oi.quantity) AS totalQty, SUM(oi.quantity * oi.unitPrice) AS revenue
            FROM OrderItem oi
            JOIN oi.product p
            WHERE oi.createdAt >= :from
            GROUP BY p.id, p.name
            ORDER BY totalQty DESC
            """)
    List<Object[]> topSellingProducts(
            @Param("from") LocalDateTime from,
            org.springframework.data.domain.Pageable pageable
    );

    // 상품별 기간 판매량 (커버링 인덱스 활용 가능: product_id, quantity, unit_price)
    @Query(value = """
            SELECT product_id,
                   SUM(quantity)              AS total_qty,
                   SUM(quantity * unit_price) AS total_revenue
            FROM order_item
            WHERE product_id = :productId
              AND created_at >= :from
            GROUP BY product_id
            """, nativeQuery = true)
    List<Object[]> salesSummaryByProduct(
            @Param("productId") Long productId,
            @Param("from") LocalDateTime from
    );
}
