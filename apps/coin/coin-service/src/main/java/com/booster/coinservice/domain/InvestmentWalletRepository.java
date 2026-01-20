package com.booster.coinservice.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvestmentWalletRepository extends JpaRepository<InvestmentWallet, Long> {
    Optional<InvestmentWallet> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM InvestmentWallet w WHERE w.userId = :userId")
    Optional<InvestmentWallet> findByUserIdWithLock(@Param("userId") String userId);
}
