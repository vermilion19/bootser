package com.booster.queryburst.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Orders, Long> {

    // 회원별 주문 목록 (인덱스: member_id)
    List<Orders> findByMemberIdOrderByOrderedAtDesc(Long memberId);

    // 상태 + 기간 필터 (복합 인덱스: status, ordered_at)
    List<Orders> findByStatusAndOrderedAtBetween(
            OrderStatus status, LocalDateTime from, LocalDateTime to
    );

    // Fetch Join: 주문 + 회원 한 번에 (N+1 방지)
    @Query("""
            SELECT o FROM Orders o
            JOIN FETCH o.member
            WHERE o.status = :status
              AND o.orderedAt >= :from
            """)
    List<Orders> findWithMemberByStatusAndOrderedAtAfter(
            @Param("status") OrderStatus status,
            @Param("from") LocalDateTime from
    );

    // 월별 매출 집계 (DATE_TRUNC 활용)
    @Query(value = """
            SELECT DATE_TRUNC('month', ordered_at) AS month,
                   COUNT(*)                        AS order_count,
                   SUM(total_amount)               AS revenue
            FROM orders
            WHERE status = 'DELIVERED'
            GROUP BY DATE_TRUNC('month', ordered_at)
            ORDER BY month DESC
            """, nativeQuery = true)
    List<Object[]> monthlyRevenue();

    // 회원 등급별 평균 주문금액 (JOIN 집계)
    @Query("""
            SELECT m.grade, AVG(o.totalAmount), COUNT(o)
            FROM Orders o
            JOIN o.member m
            WHERE o.status = 'DELIVERED'
            GROUP BY m.grade
            ORDER BY AVG(o.totalAmount) DESC
            """)
    List<Object[]> avgAmountByMemberGrade();

    // 커서 기반 페이징 (OFFSET 대비 성능 비교)
    @Query("""
            SELECT o FROM Orders o
            WHERE o.member.id = :memberId
              AND o.id < :lastId
            ORDER BY o.id DESC
            """)
    List<Orders> findByMemberIdWithCursor(
            @Param("memberId") Long memberId,
            @Param("lastId") Long lastId,
            org.springframework.data.domain.Pageable pageable
    );
}
