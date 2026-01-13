package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.infastructure.RestaurantClient;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
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

    // 1. name: yml에 설정한 'restaurantService'와 정확히 일치해야 함
    // 2. type: SEMAPHORE (스레드 풀 생성이 아닌 세마포어 방식 사용 - 일반적인 스프링 MVC 권장)
    // 3. fallbackMethod: 격벽이 꽉 차거나 에러 발생 시 실행할 메서드 이름
    @Bulkhead(name = "restaurantService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackGetRestaurant")
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

    // Fallback 메서드 구현
    // 조건: 원본 메서드와 파라미터가 같아야 하고, 마지막에 예외 파라미터를 추가해야 함
    public String fallbackGetRestaurant(Long restaurantId, BulkheadFullException e) {
        log.error("식당 조회 Bulkhead 가득 참! (요청 차단): restaurantId={}", restaurantId);

        // 대안 1: 기본값 반환
        return "조회 지연 중";

        // 대안 2: 에러를 다시 던져서 클라이언트에게 '잠시 후 시도해주세요' 메시지 전달
        // throw new CustomException(ErrorCode.SERVER_BUSY);
    }

    // (선택) 서킷 브레이커가 열렸을 때나 기타 에러용 Fallback도 필요하다면 Throwable로 잡을 수 있음
    public String fallbackGetRestaurant(Long restaurantId, Throwable t) {
        log.error("식당 조회 실패 (Circuit/Unknown Error): {}", t.getMessage());
        return "알 수 없는 식당";
    }
}
