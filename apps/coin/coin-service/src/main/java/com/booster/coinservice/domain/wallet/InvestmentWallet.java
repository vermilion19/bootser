package com.booster.coinservice.domain.wallet;

import com.booster.coinservice.domain.coinasset.CoinAsset;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class InvestmentWallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId; // 사용자 식별자

    // 초기 자본금 1억 원 (100,000,000)
    @Column(precision = 20, scale = 4)
    private BigDecimal krwBalance = new BigDecimal("100000000");

    // 보유 코인 목록
    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CoinAsset> assets = new ArrayList<>();

    public void deposit(BigDecimal amount) {
        this.krwBalance = this.krwBalance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (this.krwBalance.compareTo(amount) < 0) {
            throw new RuntimeException("잔액이 부족합니다.");
        }
        this.krwBalance = this.krwBalance.subtract(amount);
    }
}
