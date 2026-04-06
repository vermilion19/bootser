package com.booster.queryburst.lock;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 기반 분산 락 구현체.
 *
 * <h2>펜싱 토큰 발급 전략</h2>
 * 락 키별로 별도의 RAtomicLong 카운터(FENCE:{key})를 관리한다.
 * 락 획득 성공 시 INCR하여 단조 증가 토큰을 발급한다.
 * 이 토큰은 Product.lastFenceToken과 비교되어 오래된 요청을 DB 레벨에서 거부한다.
 *
 * <h2>키 구조</h2>
 * <pre>
 * 락  키: LOCK:product:42:stock      → 락 보유 여부 (TTL = leaseTime)
 * 펜싱 키: FENCE:product:42:stock     → 단조 증가 카운터 (TTL = 30일, 재시작 후 리셋 허용)
 * </pre>
 *
 * <h2>Redis 재시작 시 카운터 리셋 허용 여부</h2>
 * 카운터가 리셋(0)되면 이전에 DB에 저장된 lastFenceToken이 0보다 크므로
 * 리셋 직후 발급된 token=1이 거부될 수 있다.
 * → 운영에서는 Redis AOF/RDB 영속화를 활성화하거나, PostgreSQL 시퀀스를 사용해야 한다.
 * → 이 구현은 학습 목적으로 Redis 카운터를 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock implements DistributedLock {

    private static final String LOCK_PREFIX  = "LOCK:";
    private static final String FENCE_PREFIX = "FENCE:";

    // 펜싱 카운터 TTL: 30일 (락보다 훨씬 길게 유지)
    private static final Duration FENCE_TTL = Duration.ofDays(30);

    // 락 획득 대기 시간: 최대 2초 대기 후 실패 처리
    private static final long WAIT_TIME_SECONDS = 2L;

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    /**
     * 락 획득 + 펜싱 토큰 발급.
     *
     * <pre>
     * 1. RLock.tryLock() — waitTime 내에 락 획득 시도
     * 2. 락 획득 성공 → FENCE:{key} INCR → 단조 증가 토큰 반환
     * 3. 락 획득 실패 → LockAcquisitionException
     * </pre>
     */
    @Override
    public FencingToken tryLock(String key, Duration ttl) {
        String keyTag = toMetricTag(key);
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);

        try {
            boolean acquired = lock.tryLock(WAIT_TIME_SECONDS, ttl.toSeconds(), TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("[DistributedLock] 락 획득 실패. key={}", key);
                meterRegistry.counter("distributed_lock_failed_total", "key", keyTag).increment();
                throw new LockAcquisitionException(key);
            }

            long token = incrementFenceToken(key);
            log.debug("[DistributedLock] 락 획득 성공. key={}, token={}", key, token);
            meterRegistry.counter("distributed_lock_acquired_total", "key", keyTag).increment();
            return new FencingToken(token);

        } catch (LockAcquisitionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            meterRegistry.counter("distributed_lock_failed_total", "key", keyTag).increment();
            throw new LockAcquisitionException(key);
        } catch (Exception e) {
            log.error("[DistributedLock] Redis 연결 장애. key={}", key, e);
            meterRegistry.counter("distributed_lock_redis_error_total", "key", keyTag).increment();
            throw new RedisUnavailableException(key, e);
        }
    }

    /**
     * 락 해제.
     *
     * isHeldByCurrentThread() 체크로 타 스레드/프로세스의 락을 실수로 해제하는 것을 방지.
     * 펜싱 카운터는 해제하지 않는다 (단조 증가 유지).
     */
    @Override
    public void unlock(String key, FencingToken token) {
        try {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[DistributedLock] 락 해제 완료. key={}, token={}", key, token.value());
            } else {
                log.warn("[DistributedLock] 현재 스레드가 보유한 락이 아님 (TTL 만료 의심). key={}, token={}",
                        key, token.value());
            }
        } catch (Exception e) {
            // Redis 장애 시 TTL로 자동 만료되므로 로그만 남기고 무시
            log.warn("[DistributedLock] 락 해제 실패 (Redis 장애). key={}, token={}. TTL 만료 대기.",
                    key, token.value());
        }
    }

    /**
     * 메트릭 태그용 키 정규화.
     * "product:42:stock" → "product:*:stock" (숫자 ID 제거로 카디널리티 방지)
     */
    private String toMetricTag(String key) {
        return key.replaceAll(":\\d+", ":*");
    }

    /**
     * FENCE:{key} 카운터를 1 증가시키고 새 값을 반환한다.
     *
     * 카운터가 존재하지 않으면 Redis가 0에서 시작하여 1을 반환한다.
     * FENCE_TTL을 갱신하여 장기 미사용 키의 자동 만료를 보장한다.
     */
    private long incrementFenceToken(String key) {
        RAtomicLong counter = redissonClient.getAtomicLong(FENCE_PREFIX + key);
        long token = counter.incrementAndGet();

        // 카운터 TTL 갱신 (만료 방지)
        counter.expire(FENCE_TTL);

        return token;
    }
}
