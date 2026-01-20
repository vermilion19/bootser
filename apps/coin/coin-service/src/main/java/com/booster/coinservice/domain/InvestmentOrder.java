package com.booster.coinservice.domain;


import com.booster.coinservice.domain.enums.OrderStatus;
import com.booster.coinservice.domain.enums.OrderType;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "investment_order")
public class InvestmentOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String coinCode; // KRW-BTC

    @Enumerated(EnumType.STRING)
    private OrderType orderType; // BUY_MARKET 등

    @Enumerated(EnumType.STRING)
    private OrderStatus status; // PENDING, FILLED 등

    // 희망 가격 (시장가는 0 또는 체결될 당시 가격)
    @Column(precision = 20, scale = 4)
    private BigDecimal price;

    private BigDecimal quantity;

    private LocalDateTime filledAt;

    @Builder
    public InvestmentOrder(String userId, String coinCode, OrderType orderType, OrderStatus status, BigDecimal price, BigDecimal quantity) {
        this.userId = userId;
        this.coinCode = coinCode;
        this.orderType = orderType;
        this.status = status;
        this.price = price;
        this.quantity = quantity;
    }

    public void fill() {
        this.status = OrderStatus.FILLED;
        this.filledAt = LocalDateTime.now();
    }

}
