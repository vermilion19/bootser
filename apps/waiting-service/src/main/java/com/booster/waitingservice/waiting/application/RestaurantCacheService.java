package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.infastructure.RestaurantClient;
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
    private final RestaurantClient restaurantClient;

    private static final String KEY_PREFIX = "restaurant:name:";

    public String getRestaurantName(Long restaurantId) {
        String key = KEY_PREFIX + restaurantId;

        // ⚡️ Redis 조회 (DB 조회 X)
        String cachedName = redisTemplate.opsForValue().get(key);

        if (cachedName != null) {
            return cachedName;
        }

        // 2. Cache Miss -> Feign으로 원본 서비스 호출 (Read-Through)
        try {
            log.info("Cache Miss! Fetching from Restaurant Service. ID={}", restaurantId);

            // HTTP 요청 발생 📡
            RestaurantClient.RestaurantResponse response = restaurantClient.getRestaurant(restaurantId);

            String realName = response.name();

            // 3. Redis에 적재 (다음엔 캐시 쓰도록)
            redisTemplate.opsForValue().set(key, realName, Duration.ofHours(24));

            return realName;

        } catch (Exception e) {
            // 🚨 식당 서비스가 죽었거나 에러가 난 경우
            log.error("식당 서비스 호출 실패: {}", e.getMessage());
            return "알 수 없는 식당 (일시적 오류)"; // Fallback
        }
    }

    public void updateCache(Long restaurantId, String newName) {
        String key = KEY_PREFIX + restaurantId;
        redisTemplate.opsForValue().set(key, newName, Duration.ofHours(24));
    }
}
