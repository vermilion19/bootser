package com.booster.coinservice.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Entity
@Getter
@NoArgsConstructor
@Table(name = "investment_wallet")
public class InvestmentWallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId; // 사용자 식별자

    private BigDecimal balance;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CoinAsset> assets = new ArrayList<>();

    public InvestmentWallet(String userId) {
        this.userId = userId;
        this.balance = new BigDecimal("1000000000"); // 10억
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    public void withdraw(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }
        this.balance = this.balance.subtract(amount);
    }
}
