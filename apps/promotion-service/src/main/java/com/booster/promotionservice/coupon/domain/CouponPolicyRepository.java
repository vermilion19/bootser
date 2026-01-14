package com.booster.promotionservice.coupon.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponPolicyRepository extends JpaRepository<CouponPolicy, Long> {

    Optional<CouponPolicy> findByIdAndStatus(Long id, PolicyStatus status);
}
