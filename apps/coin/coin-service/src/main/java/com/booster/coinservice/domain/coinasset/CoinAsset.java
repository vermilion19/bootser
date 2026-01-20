package com.booster.coinservice.domain.coinasset;

import com.booster.coinservice.domain.wallet.InvestmentWallet;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class CoinAsset extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String coinCode; // KRW-BTC 등

    @Column(precision = 20, scale = 8)
    private BigDecimal amount = BigDecimal.ZERO; // 보유 수량

    @Column(precision = 20, scale = 4)
    private BigDecimal averagePrice = BigDecimal.ZERO; // 평균 매수가

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private InvestmentWallet wallet;

    // 추가 매수 시 평단가 갱신 로직 (이동평균법)
    public void addAsset(BigDecimal newAmount, BigDecimal price) {
        BigDecimal totalValue = this.amount.multiply(this.averagePrice)
                .add(newAmount.multiply(price));
        this.amount = this.amount.add(newAmount);

        if (this.amount.compareTo(BigDecimal.ZERO) > 0) {
            this.averagePrice = totalValue.divide(this.amount, 4, RoundingMode.HALF_UP);
        }
    }
}
