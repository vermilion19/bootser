package com.booster.promotionservice.application;


import com.booster.promotionservice.exception.CouponSoldOutException;
import com.booster.promotionservice.exception.DuplicateCouponIssueException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
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
        long now = System.currentTimeMillis();

        // 1. Redis 실행 (재고 감소 + Outbox 저장)
        String result = redisTemplate.execute(
                issueScript,
                List.of(
                        "coupon:count:" + couponId,
                        "coupon:issued:" + couponId,
                        "coupon:outbox:" + couponId // KEYS[3] 추가
                ),
                String.valueOf(userId),
                String.valueOf(now)
        );

        if ("DUPLICATED".equals(result)) throw new DuplicateCouponIssueException(couponId, userId);
        if ("SOLD_OUT".equals(result)) throw new CouponSoldOutException(couponId);

        // 2. Kafka 발행
        publishEvent(couponId, userId);
    }

    private void publishEvent(Long couponId, Long userId) {
        try {
            // Kafka 전송
            kafkaTemplate.send("coupon.issue.request", userId + ":" + couponId)
                    .whenComplete((sendResult, throwable) -> {
                        if (throwable == null) {
                            // 3. 전송 성공 시 Outbox에서 삭제 (Fast Path)
                            redisTemplate.opsForHash().delete("coupon:outbox:" + couponId, String.valueOf(userId));
                            log.info("Kafka 전송 완료 및 Outbox 삭제: user={}, coupon={}", userId, couponId);
                        } else {
                            // 실패 시 로그만 남김 (삭제 안 함 -> 나중에 스케줄러가 처리)
                            log.error("Kafka 전송 실패 (Outbox에 남음): user={}, coupon={}", userId, couponId, throwable);
                        }
                    });
        } catch (Exception e) {
            log.error("Kafka 발행 중 예외 발생", e);
        }
    }

}