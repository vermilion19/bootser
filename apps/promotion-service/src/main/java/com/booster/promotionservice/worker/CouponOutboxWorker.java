package com.booster.promotionservice.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponOutboxWorker {
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 1분마다 실행 (실무에서는 더 짧게 하거나, 이벤트 기반 트리거 사용)
    @Scheduled(fixedRate = 60000)
    public void resendFailedEvents() {
        // 실제로는 활성화된 쿠폰 ID 목록을 가져와서 루프를 돌거나,
        // Scan 명령어로 'coupon:outbox:*' 패턴을 찾아야 합니다.
        // 여기서는 예시로 특정 ID들에 대해서만 스캔한다고 가정하거나, 패턴 스캔을 구현합니다.

        // 예: 모든 Outbox 키 스캔 (성능 주의: 운영에선 SCAN 명령어 필수)
        Set<String> keys = redisTemplate.keys("coupon:outbox:*");
        if (keys == null) return;

        for (String key : keys) {
            String couponId = key.split(":")[2];

            // HGETALL 대신 SCAN을 권장하지만, Outbox 크기가 작다면 entries()도 무방
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String userId = (String) entry.getKey();

                log.info("Outbox 재발행 시도: user={}, coupon={}", userId, couponId);

                kafkaTemplate.send("coupon.issue.request", userId + ":" + couponId)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                // 성공 시 삭제
                                redisTemplate.opsForHash().delete(key, userId);
                            } else {
                                log.error("Outbox 재발행 실패", ex);
                            }
                        });
            }
        }
    }
}
