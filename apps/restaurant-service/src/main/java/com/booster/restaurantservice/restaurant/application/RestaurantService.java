package com.booster.restaurantservice.restaurant.application;

import com.booster.restaurantservice.restaurant.domain.Restaurant;
import com.booster.restaurantservice.restaurant.domain.RestaurantRepository;
import com.booster.restaurantservice.restaurant.web.dto.RegisterRestaurantRequest;
import com.booster.restaurantservice.restaurant.web.dto.RestaurantResponse;
import com.booster.restaurantservice.restaurant.web.dto.UpdateRestaurantRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final StringRedisTemplate redisTemplate;

    // 🔑 Waiting Service와 공유하는 키 규칙 (토씨 하나 틀리면 안됨!)
    private static final String KEY_PREFIX = "restaurant:name:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    // 1. 식당 등록
    public RestaurantResponse register(RegisterRestaurantRequest request) {
        Restaurant restaurant = Restaurant.create(
                request.name(),
                request.capacity(),
                request.maxWaitingLimit()
        );
        Restaurant saved = restaurantRepository.save(restaurant);
        // 🚀 [Redis] 식당 이름 캐시 등록 (Write-Through)
        String key = KEY_PREFIX + restaurant.getId();
        redisTemplate.opsForValue().set(key, restaurant.getName(), CACHE_TTL);
        log.info("Redis Cache Saved: id={}, name={}", restaurant.getId(), restaurant.getName());
        return RestaurantResponse.from(saved);
    }

    // 2. 단건 조회
    public RestaurantResponse getRestaurant(Long restaurantId) {
        Restaurant restaurant = findByIdOrThrow(restaurantId);
        return RestaurantResponse.from(restaurant);
    }

    // 3. 정보 수정 (이름, 용량 등)
    public RestaurantResponse update(Long restaurantId, UpdateRestaurantRequest request) {
        Restaurant restaurant = findByIdOrThrow(restaurantId);

        // Dirty Checking을 이용한 업데이트
        restaurant.updateInfo(request.name(), request.capacity(), request.maxWaitingLimit());
        // 🚀 [Redis] 캐시 덮어쓰기 (Update)
        String key = KEY_PREFIX + restaurantId;
        redisTemplate.opsForValue().set(key, restaurant.getName(), CACHE_TTL);
        log.info("Redis Cache Updated: id={}, name={}", restaurantId, restaurant.getName());
        return RestaurantResponse.from(restaurant);
    }

    // 4. 영업 상태 변경 (OPEN/CLOSE)
    public void open(Long restaurantId) {
        Restaurant restaurant = findByIdOrThrow(restaurantId);
        restaurant.open();
    }

    public void close(Long restaurantId) {
        Restaurant restaurant = findByIdOrThrow(restaurantId);
        restaurant.close();
    }

    // 5. 손님 입장 (Atomic)
    public void enter(Long restaurantId,int partySize) {
        // Atomic Update 실행
        int updatedRows = restaurantRepository.increaseOccupancy(restaurantId,partySize);

        if (updatedRows == 0) {
            throw new IllegalStateException("만석이라 입장할 수 없습니다.");
        }
    }

    // 6. 손님 퇴장 (Atomic)
    public void exit(Long restaurantId, int partySize) {
        int updatedRows = restaurantRepository.decreaseOccupancy(restaurantId, partySize);

        if (updatedRows == 0) {
            // 이미 0명인데 퇴장 처리를 시도한 경우 등
            throw new IllegalStateException("현재 입장 중인 손님이 없습니다.");
        }
    }

    public List<RestaurantResponse> getAllRestaurants() {
        return restaurantRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(RestaurantResponse::from)
                .toList();
    }

    public void deleteRestaurant(Long restaurantId) {
        restaurantRepository.deleteById(restaurantId);

        // 🚀 [Redis] 캐시 삭제 (Evict)
        String key = KEY_PREFIX + restaurantId;
        redisTemplate.delete(key);
        log.info("Redis Cache Deleted: id={}", restaurantId);
    }

    private Restaurant findByIdOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다. ID: " + id));
    }
}
