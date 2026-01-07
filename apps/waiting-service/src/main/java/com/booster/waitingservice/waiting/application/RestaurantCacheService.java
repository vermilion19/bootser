package com.booster.waitingservice.waiting.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantCacheService {
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "restaurant:name:";

    public String getRestaurantName(Long restaurantId) {
        String key = KEY_PREFIX + restaurantId;

        // ⚡️ Redis 조회 (DB 조회 X)
        String cachedName = redisTemplate.opsForValue().get(key);

        if (cachedName != null) {
            return cachedName;
        }

        // Cache Miss: 식당 서비스가 아직 캐시를 안 넣었거나, 만료된 경우
        // Waiting Service는 DB 접근 권한이 없으므로 '기본값' 반환
        log.warn("🚨 Cache Miss! 식당 이름을 찾을 수 없습니다. ID={}", restaurantId);
        return "(알 수 없는 식당)";
    }
}
