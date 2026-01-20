package com.booster.coinservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvestmentOrderRepository extends JpaRepository<InvestmentOrder, Long> {

    /**
     * [매칭 엔진용 쿼리]
     * 지정가 매수(BUY_LIMIT) 주문 중, 현재가보다 비싸거나 같게 사겠다고 한 주문들을 찾음.
     * (예: 난 100원에 살래! 했는데 가격이 90원이 됨 -> 체결!)
     * -- 매칭 엔진 성능을 위한 복합 인덱스
     * CREATE INDEX idx_order_matching
     * ON investment_order (coin_code, status, order_type, price);
     */
    @Query("SELECT o FROM InvestmentOrder o " +
            "WHERE o.coinCode = :coinCode " +
            "AND o.status = 'PENDING' " +
            "AND o.orderType = 'BUY_LIMIT' " +
            "AND o.price >= :currentPrice " + // [중요] 내 희망가 >= 현재가
            "ORDER BY o.createdAt ASC")       // 먼저 주문한 사람부터 (선착순)
    List<InvestmentOrder> findBuyableOrders(
            @Param("coinCode") String coinCode,
            @Param("currentPrice") BigDecimal currentPrice
    );

    // 특정 사용자의 주문 내역 조회 (최신순)
    List<InvestmentOrder> findByUserIdOrderByCreatedAtDesc(String userId);
}
