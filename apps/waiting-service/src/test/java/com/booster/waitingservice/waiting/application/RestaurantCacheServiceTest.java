package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.infastructure.RestaurantClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantCacheService 테스트")
class RestaurantCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RestaurantClient restaurantClient;

    @InjectMocks
    private RestaurantCacheService restaurantCacheService;

    private static final String KEY_PREFIX = "restaurant:name:";

    @Nested
    @DisplayName("getRestaurantName 메서드")
    class GetRestaurantName {

        @Test
        @DisplayName("성공 - Cache Hit: Redis에 데이터가 있으면 Feign 호출 없이 캐시 값을 반환한다")
        void getRestaurantName_cacheHit() {
            // given
            Long restaurantId = 1L;
            String expectedName = "맛있는 식당";
            String key = KEY_PREFIX + restaurantId;

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(key)).willReturn(expectedName);

            // when
            String result = restaurantCacheService.getRestaurantName(restaurantId);

            // then
            assertThat(result).isEqualTo(expectedName);
            verify(restaurantClient, never()).getRestaurant(any()); // Feign 호출 안함
        }

        @Test
        @DisplayName("성공 - Cache Miss: Redis에 데이터가 없으면 Feign으로 조회 후 캐시에 저장한다")
        void getRestaurantName_cacheMiss() {
            // given
            Long restaurantId = 1L;
            String expectedName = "새로운 식당";
            String key = KEY_PREFIX + restaurantId;

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(key)).willReturn(null); // Cache Miss

            RestaurantClient.RestaurantResponse response =
                    new RestaurantClient.RestaurantResponse(restaurantId, expectedName, "서울시 강남구");
            given(restaurantClient.getRestaurant(restaurantId)).willReturn(response);

            // when
            String result = restaurantCacheService.getRestaurantName(restaurantId);

            // then
            assertThat(result).isEqualTo(expectedName);
            verify(restaurantClient).getRestaurant(restaurantId); // Feign 호출 확인
            verify(valueOperations).set(eq(key), eq(expectedName), any(Duration.class)); // 캐시 저장 확인
        }

        @Test
        @DisplayName("실패 - Feign 호출 실패 시 Fallback 메시지를 반환한다")
        void getRestaurantName_feignError() {
            // given
            Long restaurantId = 1L;
            String key = KEY_PREFIX + restaurantId;

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(key)).willReturn(null); // Cache Miss
            given(restaurantClient.getRestaurant(restaurantId))
                    .willThrow(new RuntimeException("Connection refused"));

            // when
            String result = restaurantCacheService.getRestaurantName(restaurantId);

            // then
            assertThat(result).isEqualTo("알 수 없는 식당 (일시적 오류)");
        }
    }

    @Nested
    @DisplayName("updateCache 메서드")
    class UpdateCache {

        @Test
        @DisplayName("성공: 캐시에 새로운 값을 저장한다")
        void updateCache_success() {
            // given
            Long restaurantId = 1L;
            String newName = "업데이트된 식당";
            String key = KEY_PREFIX + restaurantId;

            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            restaurantCacheService.updateCache(restaurantId, newName);

            // then
            verify(valueOperations).set(eq(key), eq(newName), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("fallbackGetRestaurant 메서드")
    class FallbackGetRestaurant {

        @Test
        @DisplayName("Bulkhead 가득 참 예외 발생 시 '조회 지연 중' 메시지를 반환한다")
        void fallback_bulkheadFull() {
            // given
            Long restaurantId = 1L;
            // BulkheadFullException은 Bulkhead 인스턴스가 필요하므로 실제 Bulkhead 생성
            Bulkhead bulkhead = Bulkhead.ofDefaults("testBulkhead");
            BulkheadFullException exception = BulkheadFullException.createBulkheadFullException(bulkhead);

            // when
            String result = restaurantCacheService.fallbackGetRestaurant(restaurantId, exception);

            // then
            assertThat(result).isEqualTo("서버가 바쁩니다. 잠시 후 시도해주세요.");
        }

        @Test
        @DisplayName("기타 Throwable 예외 발생 시 '알 수 없는 식당' 메시지를 반환한다")
        void fallback_throwable() {
            // given
            Long restaurantId = 1L;
            Throwable exception = new RuntimeException("Unknown error");

            // when
            String result = restaurantCacheService.fallbackGetRestaurant(restaurantId, exception);

            // then
            assertThat(result).isEqualTo("알 수 없는 식당");
        }
    }
}