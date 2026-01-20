package com.booster.coinservice.domain.coinasset;

import com.booster.coinservice.domain.wallet.InvestmentWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoinAssetRepository extends JpaRepository<CoinAsset,Long> {
    Optional<CoinAsset> findByWalletAndCoinCode(InvestmentWallet wallet, String coinCode);
}
