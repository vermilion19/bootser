package com.booster.queryburst.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Kafka Consumer 멱등성 서비스.
 *
 * <h2>필요성</h2>
 * Outbox 패턴은 At-Least-Once 발행을 보장한다.
 * 즉, 동일 이벤트가 Kafka에 2회 이상 발행될 수 있다.
 * Consumer가 멱등성을 갖추지 않으면 통계 중복 집계, 랭킹 과다 산정 등의 문제가 발생한다.
 *
 * <h2>Redis 키 구조</h2>
 * <pre>
 * CONSUMER:{groupId}:{orderId}:{eventType}
 *   → "PROCESSED"  (TTL: 25시간)
 * </pre>
 *
 * TTL = Kafka retention(기본 24h) + 여유 1h.
 * Kafka retention 이후 동일 메시지가 재전달될 일이 없으므로
 * 그 이상 캐시할 필요가 없다.
 *
 * <h2>사용 방법</h2>
 * <pre>
 * if (idempotencyService.isDuplicate(groupId, orderId, eventType)) return;
 * // ... 비즈니스 로직 처리 ...
 * idempotencyService.markProcessed(groupId, orderId, eventType);
 * </pre>
 *
 * <h2>주의</h2>
 * markProcessed는 비즈니스 로직 처리 후 호출한다.
 * 처리 전 호출 시 장애 발생 후 재시도가 불가능해진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerIdempotencyService {

    private static final String KEY_PREFIX = "CONSUMER:";
    private static final Duration TTL = Duration.ofHours(25);

    private final RedissonClient redissonClient;

    /**
     * 이미 처리된 이벤트인지 확인.
     *
     * @param groupId   Consumer Group ID
     * @param orderId   주문 ID (이벤트 식별자)
     * @param eventType 이벤트 타입 (ORDER_CREATED, ORDER_CANCELED 등)
     * @return true = 중복 (처리 건너뜀), false = 신규 (처리 필요)
     */
    public boolean isDuplicate(String groupId, Long orderId, String eventType) {
        String key = buildKey(groupId, orderId, eventType);
        boolean exists = redissonClient.getBucket(key).isExists();
        if (exists) {
            log.warn("[ConsumerIdempotency] 중복 이벤트 감지. key={}", key);
        }
        return exists;
    }

    /**
     * 처리 완료 마킹.
     *
     * 비즈니스 로직 처리 성공 후 호출.
     * setIfAbsent로 동시 중복 처리 방지.
     */
    public void markProcessed(String groupId, Long orderId, String eventType) {
        String key = buildKey(groupId, orderId, eventType);
        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.setIfAbsent("PROCESSED", TTL);
        log.debug("[ConsumerIdempotency] 처리 완료 마킹. key={}", key);
    }

    private String buildKey(String groupId, Long orderId, String eventType) {
        return KEY_PREFIX + groupId + ":" + orderId + ":" + eventType;
    }
}
