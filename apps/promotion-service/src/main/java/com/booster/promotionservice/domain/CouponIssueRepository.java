package com.booster.promotionservice.domain;

import com.booster.promotionservice.domain.entity.CouponIssue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {
    // 중복 발급 여부 확인용 (이미 Redis에서 막지만, 혹시 몰라 DB 조회용으로 남겨둠)
    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
