package com.booster.coinservice.domain.investmentorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvestmentOrderRepository extends JpaRepository<InvestmentOrder,Long> {

    /**
     * [매칭 엔진 핵심 쿼리]
     * 지정가 매수(BUY_LIMIT) 주문 중 체결 조건이 만족된 주문들을 찾습니다.
     * * 조건:
     * 1. 해당 코인(coinCode)이어야 함
     * 2. 상태가 대기(PENDING)여야 함
     * 3. 주문 타입이 매수(BUY_LIMIT)여야 함
     * 4. [중요] 타겟 가격(내가 사려는 가격) >= 현재 가격
     * (예: 나는 500원에 사고 싶은데, 현재가가 400원이 됨 -> 싸니까 사야 함!)
     * 인덱스 필요함
     *  -- PostgreSQL / MySQL 예시
     * CREATE INDEX idx_order_matching
     * ON investment_order (coin_code, status, order_type, target_price);
     */
    @Query("SELECT o FROM InvestmentOrder o " +
            "WHERE o.coinCode = :coinCode " +
            "AND o.status = :status " +
            "AND o.orderType = 'BUY_LIMIT' " +
            "AND o.targetPrice >= :currentPrice " +
            "ORDER BY o.createdAt ASC") // 먼저 주문한 사람부터 체결 (FIFO)
    List<InvestmentOrder> findPendingBuyOrders(
            @Param("coinCode") String coinCode,
            @Param("currentPrice") BigDecimal currentPrice,
            @Param("status") OrderStatus status
    );

    // 참고: 사용자의 주문 내역 조회용
    List<InvestmentOrder> findByUserIdOrderByCreatedAtDesc(String userId);
}
