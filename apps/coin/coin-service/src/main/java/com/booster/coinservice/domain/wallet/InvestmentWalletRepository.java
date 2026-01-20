package com.booster.coinservice.domain.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestmentWalletRepository extends JpaRepository<InvestmentWallet,Long> {

    Optional<InvestmentWallet> findByUserId(String userId);
}
