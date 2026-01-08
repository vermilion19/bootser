package com.booster.restaurantservice.restaurant.application;

import com.booster.restaurantservice.restaurant.domain.Restaurant;
import com.booster.restaurantservice.restaurant.domain.RestaurantRepository;
import com.booster.restaurantservice.restaurant.domain.RestaurantStatus; // Assuming this enum exists
import com.booster.restaurantservice.restaurant.web.dto.RegisterRestaurantRequest;
import com.booster.restaurantservice.restaurant.web.dto.RestaurantResponse;
import com.booster.restaurantservice.restaurant.web.dto.UpdateRestaurantRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @InjectMocks
    private RestaurantService restaurantService;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String KEY_PREFIX = "restaurant:name:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    @BeforeEach
    void setUp() {
        // RedisTemplate의 opsForValue()가 valueOperations를 반환하도록 설정
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @DisplayName("식당을 성공적으로 등록한다")
    @Test
    void registerRestaurantSuccessfully() {
        // given
        RegisterRestaurantRequest request = new RegisterRestaurantRequest("테스트식당", 50, 10);
        Restaurant newRestaurant = Restaurant.create(request.name(), request.capacity(), request.maxWaitingLimit());
        ReflectionTestUtils.setField(newRestaurant, "id", 1L); // ID 설정
        ReflectionTestUtils.setField(newRestaurant, "status", RestaurantStatus.CLOSED); // 초기 상태 설정

        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(newRestaurant);

        // when
        RestaurantResponse response = restaurantService.register(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("테스트식당");
        assertThat(response.capacity()).isEqualTo(50);
        assertThat(response.maxWaitingLimit()).isEqualTo(10);
        assertThat(response.status()).isEqualTo(RestaurantStatus.CLOSED); // 초기 상태 확인
        verify(restaurantRepository, times(1)).save(any(Restaurant.class));
        verify(valueOperations, times(1)).set(eq(KEY_PREFIX + 1L), eq("테스트식당"), eq(CACHE_TTL));
    }

    @DisplayName("ID로 식당을 성공적으로 조회한다")
    @Test
    void getRestaurantByIdSuccessfully() {
        // given
        Long restaurantId = 1L;
        Restaurant restaurant = Restaurant.create("조회식당", 30, 5);
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        ReflectionTestUtils.setField(restaurant, "status", RestaurantStatus.OPEN);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // when
        RestaurantResponse response = restaurantService.getRestaurant(restaurantId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(restaurantId);
        assertThat(response.name()).isEqualTo("조회식당");
        assertThat(response.status()).isEqualTo(RestaurantStatus.OPEN);
        verify(restaurantRepository, times(1)).findById(restaurantId);
    }

    @DisplayName("존재하지 않는 ID로 식당 조회 시 EntityNotFoundException을 발생시킨다")
    @Test
    void getRestaurantByIdNotFoundThrowsException() {
        // given
        Long nonExistentId = 99L;
        when(restaurantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> restaurantService.getRestaurant(nonExistentId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + nonExistentId);
        verify(restaurantRepository, times(1)).findById(nonExistentId);
    }

    @DisplayName("식당 정보를 성공적으로 수정한다")
    @Test
    void updateRestaurantInfoSuccessfully() {
        // given
        Long restaurantId = 1L;
        UpdateRestaurantRequest request = new UpdateRestaurantRequest("수정된식당", 60, 15);
        Restaurant existingRestaurant = Restaurant.create("기존식당", 50, 10);
        ReflectionTestUtils.setField(existingRestaurant, "id", restaurantId);
        ReflectionTestUtils.setField(existingRestaurant, "status", RestaurantStatus.OPEN);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existingRestaurant));

        // when
        RestaurantResponse response = restaurantService.update(restaurantId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(restaurantId);
        assertThat(response.name()).isEqualTo("수정된식당");
        assertThat(response.capacity()).isEqualTo(60);
        assertThat(response.maxWaitingLimit()).isEqualTo(15);
        assertThat(response.status()).isEqualTo(RestaurantStatus.OPEN); // 상태는 변경되지 않음
        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(valueOperations, times(1)).set(eq(KEY_PREFIX + restaurantId), eq("수정된식당"), eq(CACHE_TTL));
    }

    @DisplayName("존재하지 않는 식당 정보 수정 시 EntityNotFoundException을 발생시킨다")
    @Test
    void updateRestaurantInfoNotFoundThrowsException() {
        // given
        Long nonExistentId = 99L;
        UpdateRestaurantRequest request = new UpdateRestaurantRequest("수정된식당", 60, 15);
        when(restaurantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> restaurantService.update(nonExistentId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + nonExistentId);
        verify(restaurantRepository, times(1)).findById(nonExistentId);
    }

    @DisplayName("식당을 성공적으로 오픈 상태로 변경한다")
    @Test
    void openRestaurantSuccessfully() {
        // given
        Long restaurantId = 1L;
        Restaurant restaurant = Restaurant.create("오픈식당", 30, 5);
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        ReflectionTestUtils.setField(restaurant, "status", RestaurantStatus.CLOSED); // 초기 상태는 닫힘

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // when
        restaurantService.open(restaurantId);

        // then
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.OPEN);
        verify(restaurantRepository, times(1)).findById(restaurantId);
    }

    @DisplayName("존재하지 않는 식당 오픈 시 EntityNotFoundException을 발생시킨다")
    @Test
    void openRestaurantNotFoundThrowsException() {
        // given
        Long nonExistentId = 99L;
        when(restaurantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> restaurantService.open(nonExistentId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + nonExistentId);
        verify(restaurantRepository, times(1)).findById(nonExistentId);
    }

    @DisplayName("식당을 성공적으로 닫힘 상태로 변경한다")
    @Test
    void closeRestaurantSuccessfully() {
        // given
        Long restaurantId = 1L;
        Restaurant restaurant = Restaurant.create("닫힘식당", 30, 5);
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        ReflectionTestUtils.setField(restaurant, "status", RestaurantStatus.OPEN); // 초기 상태는 열림

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // when
        restaurantService.close(restaurantId);

        // then
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.CLOSED);
        verify(restaurantRepository, times(1)).findById(restaurantId);
    }

    @DisplayName("존재하지 않는 식당 닫힘 시 EntityNotFoundException을 발생시킨다")
    @Test
    void closeRestaurantNotFoundThrowsException() {
        // given
        Long nonExistentId = 99L;
        when(restaurantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> restaurantService.close(nonExistentId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + nonExistentId);
        verify(restaurantRepository, times(1)).findById(nonExistentId);
    }

    @DisplayName("식당에 손님이 성공적으로 입장한다")
    @Test
    void enterRestaurantSuccessfully() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        when(restaurantRepository.increaseOccupancy(restaurantId, partySize)).thenReturn(1); // 1행 업데이트 성공

        // when
        restaurantService.enter(restaurantId, partySize);

        // then
        verify(restaurantRepository, times(1)).increaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("만석으로 인해 손님 입장 실패 시 IllegalStateException을 발생시킨다")
    @Test
    void enterRestaurantWhenFullThrowsException() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        when(restaurantRepository.increaseOccupancy(restaurantId, partySize)).thenReturn(0); // 0행 업데이트 (만석)

        // when, then
        assertThatThrownBy(() -> restaurantService.enter(restaurantId, partySize))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("만석이라 입장할 수 없습니다.");
        verify(restaurantRepository, times(1)).increaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("식당에서 손님이 성공적으로 퇴장한다")
    @Test
    void exitRestaurantSuccessfully() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        when(restaurantRepository.decreaseOccupancy(restaurantId, partySize)).thenReturn(1); // 1행 업데이트 성공

        // when
        restaurantService.exit(restaurantId, partySize);

        // then
        verify(restaurantRepository, times(1)).decreaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("현재 입장 중인 손님이 없어 퇴장 실패 시 IllegalStateException을 발생시킨다")
    @Test
    void exitRestaurantWhenNoOccupancyThrowsException() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        when(restaurantRepository.decreaseOccupancy(restaurantId, partySize)).thenReturn(0); // 0행 업데이트 (손님 없음)

        // when, then
        assertThatThrownBy(() -> restaurantService.exit(restaurantId, partySize))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("현재 입장 중인 손님이 없습니다.");
        verify(restaurantRepository, times(1)).decreaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("모든 식당 목록을 성공적으로 조회한다")
    @Test
    void getAllRestaurantsSuccessfully() {
        // given
        Restaurant restaurant1 = Restaurant.create("식당1", 20, 5);
        ReflectionTestUtils.setField(restaurant1, "id", 1L);
        ReflectionTestUtils.setField(restaurant1, "status", RestaurantStatus.OPEN);

        Restaurant restaurant2 = Restaurant.create("식당2", 40, 10);
        ReflectionTestUtils.setField(restaurant2, "id", 2L);
        ReflectionTestUtils.setField(restaurant2, "status", RestaurantStatus.CLOSED);

        List<Restaurant> restaurants = Arrays.asList(restaurant1, restaurant2);
        when(restaurantRepository.findAll(any(Sort.class))).thenReturn(restaurants);

        // when
        List<RestaurantResponse> responses = restaurantService.getAllRestaurants();

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).name()).isEqualTo("식당1");
        assertThat(responses.get(0).status()).isEqualTo(RestaurantStatus.OPEN);
        assertThat(responses.get(1).id()).isEqualTo(2L);
        assertThat(responses.get(1).name()).isEqualTo("식당2");
        assertThat(responses.get(1).status()).isEqualTo(RestaurantStatus.CLOSED);
        verify(restaurantRepository, times(1)).findAll(any(Sort.class));
    }

    @DisplayName("식당을 성공적으로 삭제한다")
    @Test
    void deleteRestaurantSuccessfully() {
        // given
        Long restaurantId = 1L;

        // when
        restaurantService.deleteRestaurant(restaurantId);

        // then
        verify(restaurantRepository, times(1)).deleteById(restaurantId);
        verify(redisTemplate, times(1)).delete(eq(KEY_PREFIX + restaurantId));
    }
}