package com.booster.coinservice.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "coin_asset")
public class CoinAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String coinCode;

    private BigDecimal quantity;
    private BigDecimal averagePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private InvestmentWallet wallet;

    public CoinAsset(InvestmentWallet wallet, String coinCode) {
        this.wallet = wallet;
        this.coinCode = coinCode;
        this.quantity = BigDecimal.ZERO;
        this.averagePrice = BigDecimal.ZERO;
    }

    public void updateQuantity(BigDecimal amount, BigDecimal price) {
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.quantity = amount;
            this.averagePrice = price;
        } else {
            BigDecimal totalValue = this.quantity.multiply(this.averagePrice).add(amount.multiply(price));
            BigDecimal totalQuantity = this.quantity.add(amount);

            this.averagePrice = totalValue.divide(totalQuantity, 4, RoundingMode.HALF_UP);
            this.quantity = totalQuantity;
        }
    }
}
