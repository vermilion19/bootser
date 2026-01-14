package com.booster.promotionservice.coupon.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, Long> {

    boolean existsByUserIdAndCouponPolicyId(Long userId, Long couponPolicyId);

    Optional<IssuedCoupon> findByUserIdAndCouponPolicyId(Long userId, Long couponPolicyId);

    List<IssuedCoupon> findAllByUserId(Long userId);

    List<IssuedCoupon> findAllByUserIdAndStatus(Long userId, CouponStatus status);
}
