package com.booster.storage.redis.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockRepository {

    private final RedissonClient redissonClient;

    public boolean lock(String key) {
        RLock lock = redissonClient.getLock(getLockKey(key));
        try {
            return lock.tryLock(2, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Redis Lock 획득 중 인터럽트 발생: {}", key, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(String key) {
        RLock lock = redissonClient.getLock(getLockKey(key));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }


    private String getLockKey(String key) {
        return "lock:" + key;
    }
}
