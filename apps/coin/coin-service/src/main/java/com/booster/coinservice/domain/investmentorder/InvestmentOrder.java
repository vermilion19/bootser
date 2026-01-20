package com.booster.coinservice.domain.investmentorder;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class InvestmentOrder extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String coinCode;

    @Enumerated(EnumType.STRING)
    private OrderType orderType; // BUY_MARKET(시장가), BUY_LIMIT(지정가)

    @Enumerated(EnumType.STRING)
    private OrderStatus status; // PENDING(대기), FILLED(체결), CANCELED(취소)

    @Column(precision = 20, scale = 4)
    private BigDecimal targetPrice; // 지정가 (시장가는 0 or 체결가)

    @Column(precision = 20, scale = 8)
    private BigDecimal amount; // 주문 수량

    private LocalDateTime filledAt;

    @Builder
    public InvestmentOrder(String userId, String coinCode, OrderType orderType, OrderStatus status, BigDecimal targetPrice, BigDecimal amount) {
        this.userId = userId;
        this.coinCode = coinCode;
        this.orderType = orderType;
        this.status = status;
        this.targetPrice = targetPrice;
        this.amount = amount;
    }

}
