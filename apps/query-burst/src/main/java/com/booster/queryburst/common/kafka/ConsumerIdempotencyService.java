package com.booster.queryburst.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerIdempotencyService {

    private static final String KEY_PREFIX = "CONSUMER:";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(10);
    private static final Duration PROCESSED_TTL = Duration.ofHours(25);
    private static final String PROCESSING = "PROCESSING";
    private static final String PROCESSED = "PROCESSED";

    private final RedissonClient redissonClient;

    public boolean tryStartProcessing(String groupId, Long orderId, String eventType) {
        String key = buildKey(groupId, orderId, eventType);
        RBucket<String> bucket = redissonClient.getBucket(key);
        boolean acquired = bucket.setIfAbsent(PROCESSING, PROCESSING_TTL);
        if (!acquired) {
            log.warn("[ConsumerIdempotency] duplicate or processing event skipped. key={}", key);
        }
        return acquired;
    }

    public void markProcessed(String groupId, Long orderId, String eventType) {
        String key = buildKey(groupId, orderId, eventType);
        redissonClient.getBucket(key).set(PROCESSED, PROCESSED_TTL);
        log.debug("[ConsumerIdempotency] marked processed. key={}", key);
    }

    public void clearProcessing(String groupId, Long orderId, String eventType) {
        String key = buildKey(groupId, orderId, eventType);
        RBucket<String> bucket = redissonClient.getBucket(key);
        String currentState = bucket.get();
        if (PROCESSING.equals(currentState)) {
            bucket.delete();
            log.debug("[ConsumerIdempotency] cleared processing mark. key={}", key);
        }
    }

    private String buildKey(String groupId, Long orderId, String eventType) {
        return KEY_PREFIX + groupId + ":" + orderId + ":" + eventType;
    }
}
