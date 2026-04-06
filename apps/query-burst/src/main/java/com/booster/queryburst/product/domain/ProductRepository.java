package com.booster.queryburst.product.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Redis 장애 시 Fallback용 비관적 쓰기 락 조회.
     *
     * SELECT ... FOR UPDATE로 트랜잭션 종료까지 행을 잠근다.
     * 락 보유 시간 = 트랜잭션 시간이므로 fencing token 없이도 동시성 보장.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
}
