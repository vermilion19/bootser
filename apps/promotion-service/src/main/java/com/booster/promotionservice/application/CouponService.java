package com.booster.promotionservice.application;

import com.booster.promotionservice.application.dto.CreateCouponRequest;
import com.booster.promotionservice.domain.CouponRepository;
import com.booster.promotionservice.domain.entity.Coupon;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final RedisTemplate<String, String> redisTemplate;


    @Transactional
    public Long create(CreateCouponRequest request) {
        // 1. DB에 쿠폰 정책 저장 (MySQL)
        Coupon coupon = new Coupon(
                request.title(),
                request.totalQuantity(),
                request.discountAmount(),
                request.startAt(),
                request.endAt()
        );
        couponRepository.save(coupon);

        // 2. Redis에 재고 세팅 (Lua Script가 사용할 키)
        // 키 형식: coupon:count:{couponId}
        String redisKey = "coupon:count:" + coupon.getId();
        redisTemplate.opsForValue().set(redisKey, String.valueOf(request.totalQuantity()));

        return coupon.getId();
    }
}
