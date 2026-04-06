package com.booster.queryburst.order.application;

import com.booster.common.JsonUtils;
import com.booster.queryburst.order.application.dto.OrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 멱등성 키 관리 서비스.
 *
 * <h2>Redis 키 구조</h2>
 * <pre>
 * IDEMPOTENCY:{key}
 *   → {"status":"PROCESSING"}                                  (처리 중, TTL=5분)
 *   → {"status":"COMPLETED","orderId":1,"totalAmount":50000}   (완료, TTL=24시간)
 * </pre>
 *
 * <h2>흐름</h2>
 * <pre>
 * 1. checkAndMarkProcessing(key)
 *    - 키 없음          → PROCESSING 마킹 후 Proceed 반환
 *    - PROCESSING 존재  → AlreadyProcessing 반환 (클라이언트 409)
 *    - COMPLETED 존재   → AlreadyCompleted(캐시 결과) 반환
 *
 * 2. markCompleted(key, result)
 *    - 주문 완료 후 호출. COMPLETED 상태로 덮어쓰고 TTL 연장.
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "IDEMPOTENCY:";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED  = "COMPLETED";

    private final RedissonClient redissonClient;

    public IdempotencyCheck checkAndMarkProcessing(String idempotencyKey) {
        RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + idempotencyKey);

        // SET NX: 키가 없을 때만 PROCESSING으로 세팅
        IdempotencyRecord processing = new IdempotencyRecord(STATUS_PROCESSING, null, null);
        boolean isNew = bucket.setIfAbsent(JsonUtils.toJson(processing), PROCESSING_TTL);

        if (isNew) {
            log.debug("[Idempotency] 새 요청 등록. key={}", idempotencyKey);
            return new IdempotencyCheck.Proceed();
        }

        // 이미 키가 존재 → PROCESSING or COMPLETED 판별
        String existing = bucket.get();
        if (existing == null) {
            // TTL 만료 후 극히 드문 레이스 케이스: 다시 PROCESSING으로 시도
            return checkAndMarkProcessing(idempotencyKey);
        }

        IdempotencyRecord record = JsonUtils.fromJson(existing, IdempotencyRecord.class);

        if (STATUS_COMPLETED.equals(record.status())) {
            log.debug("[Idempotency] 이미 완료된 요청. key={}, orderId={}", idempotencyKey, record.orderId());
            return new IdempotencyCheck.AlreadyCompleted(
                    new OrderResult(record.orderId(), record.totalAmount())
            );
        }

        log.warn("[Idempotency] 중복 요청 감지 (처리 중). key={}", idempotencyKey);
        return new IdempotencyCheck.AlreadyProcessing();
    }

    public void markCompleted(String idempotencyKey, OrderResult result) {
        RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + idempotencyKey);
        IdempotencyRecord completed = new IdempotencyRecord(STATUS_COMPLETED, result.orderId(), result.totalAmount());
        bucket.set(JsonUtils.toJson(completed), COMPLETED_TTL);
        log.debug("[Idempotency] 완료 마킹. key={}, orderId={}", idempotencyKey, result.orderId());
    }

    // Redis 저장용 내부 레코드 (JsonUtils.MAPPER로 직렬화)
    public record IdempotencyRecord(String status, Long orderId, Long totalAmount) {}
}
