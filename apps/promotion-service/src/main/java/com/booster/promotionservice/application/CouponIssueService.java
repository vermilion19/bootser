package com.booster.promotionservice.application;


import com.booster.promotionservice.exception.CouponSoldOutException;
import com.booster.promotionservice.exception.DuplicateCouponIssueException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final RedisTemplate<String,String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // lua script 미리 컴파일
    private DefaultRedisScript<String> issueScript;

    @PostConstruct
    public void init() {
        issueScript = new DefaultRedisScript<>();
        issueScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/issue-coupon.lua")));
        issueScript.setResultType(String.class);
    }

    public void issueCoupon(Long couponId, Long userId) {
        // 1. Redis Lua Script 실행
        String result = redisTemplate.execute(
                issueScript,
                List.of("coupon:count:" + couponId, "coupon:issued:" + couponId),
                String.valueOf(userId)
        );

        // 2. 결과 처리
        if ("DUPLICATED".equals(result)) {
            throw new DuplicateCouponIssueException(couponId, userId);
        }
        if ("SOLD_OUT".equals(result)) {
            throw new CouponSoldOutException(couponId);
        }

        // 3. Kafka 이벤트 발행 (비동기 DB 저장용)
        // 실제로는 DTO로 변환해서 보내야 함
        kafkaTemplate.send("coupon.issue.request", userId + ":" + couponId);
    }
}
