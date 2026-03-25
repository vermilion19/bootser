package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.domain.RestaurantSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantCacheService {

    private final StringRedisTemplate redisTemplate;
    private final RestaurantSnapshotRepository snapshotRepository;

    private static final String KEY_PREFIX = "restaurant:name:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /**
     * 식당명 조회 (Redis → Local DB → Fallback 순서)
     * 런타임 Feign 호출 없음 - Restaurant Service 장애와 완전히 격리됨
     */
    public String getRestaurantName(Long restaurantId) {
        String key = KEY_PREFIX + restaurantId;

        // 1. Redis 조회 (L1 캐시)
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        // 2. Local DB 조회 (L2 캐시) - Redis 유실 시 복구 경로
        log.info("Redis Cache Miss. DB에서 복구 시도: restaurantId={}", restaurantId);
        return snapshotRepository.findById(restaurantId)
                .map(snapshot -> {
                    // Redis 재적재
                    redisTemplate.opsForValue().set(key, snapshot.getName(), CACHE_TTL);
                    log.info("Redis 복구 완료: restaurantId={}, name={}", restaurantId, snapshot.getName());
                    return snapshot.getName();
                })
                .orElseGet(() -> {
                    // 3. Graceful Degradation - 사용자에게 노출되지 않는 임시값
                    // Bootstrap 또는 Kafka 이벤트로 곧 채워질 예정
                    log.warn("식당 정보 없음 (Bootstrap 미완료 또는 신규 식당): restaurantId={}", restaurantId);
                    return "식당 #" + restaurantId;
                });
    }

    public void updateCache(Long restaurantId, String name) {
        String key = KEY_PREFIX + restaurantId;
        redisTemplate.opsForValue().set(key, name, CACHE_TTL);
    }

    public void evictCache(Long restaurantId) {
        redisTemplate.delete(KEY_PREFIX + restaurantId);
    }
}
