package com.booster.queryburstmsa.catalog.lock;

import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RedisDistributedLock implements DistributedLock {

    private static final String LOCK_PREFIX = "LOCK:";
    private static final String FENCE_PREFIX = "FENCE:";
    private static final Duration FENCE_TTL = Duration.ofDays(30);
    private static final long WAIT_TIME_SECONDS = 2L;

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    public RedisDistributedLock(RedissonClient redissonClient, MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public FencingToken tryLock(String key, Duration ttl) {
        String keyTag = toMetricTag(key);
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);

        try {
            boolean acquired = lock.tryLock(WAIT_TIME_SECONDS, ttl.toSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                meterRegistry.counter("distributed_lock_failed_total", "key", keyTag).increment();
                throw new LockAcquisitionException(key);
            }

            long token = incrementFenceToken(key);
            meterRegistry.counter("distributed_lock_acquired_total", "key", keyTag).increment();
            return new FencingToken(token);
        } catch (LockAcquisitionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            meterRegistry.counter("distributed_lock_failed_total", "key", keyTag).increment();
            throw new LockAcquisitionException(key);
        } catch (Exception e) {
            meterRegistry.counter("distributed_lock_redis_error_total", "key", keyTag).increment();
            throw new RedisUnavailableException(key, e);
        }
    }

    @Override
    public void unlock(String key, FencingToken token) {
        try {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception ignored) {
            // TTL expiration remains the last line of defense.
        }
    }

    private long incrementFenceToken(String key) {
        RAtomicLong counter = redissonClient.getAtomicLong(FENCE_PREFIX + key);
        long token = counter.incrementAndGet();
        counter.expire(FENCE_TTL);
        return token;
    }

    private String toMetricTag(String key) {
        return key.replaceAll(":\\d+", ":*");
    }
}
