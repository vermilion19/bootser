package com.booster.queryburst.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 주문별 항목 조회.
     * 취소/통계 이벤트 빌드 시 사용 (트랜잭션 내 lazy load 대안).
     */
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p JOIN FETCH p.category WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);
}
