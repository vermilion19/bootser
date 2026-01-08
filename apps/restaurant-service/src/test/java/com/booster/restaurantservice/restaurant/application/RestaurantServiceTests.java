package com.booster.restaurantservice.restaurant.application;

import com.booster.restaurantservice.restaurant.domain.Restaurant;
import com.booster.restaurantservice.restaurant.domain.RestaurantRepository;
import com.booster.restaurantservice.restaurant.domain.RestaurantStatus;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

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
        // given
        // redisTemplate.opsForValue() 호출 시 Mock 객체 반환하도록 설정
//        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @DisplayName("식당_등록_성공")
    @Test
    void 식당_등록_성공() {
        // given
        RegisterRestaurantRequest request = new RegisterRestaurantRequest("테스트식당", 50, 10);
        Restaurant restaurant = Restaurant.create("테스트식당", 50, 10);
        // 엔티티 ID는 ReflectionTestUtils를 사용하여 설정
        ReflectionTestUtils.setField(restaurant, "id", 1L);

        given(restaurantRepository.save(any(Restaurant.class))).willReturn(restaurant);

        // when
        RestaurantResponse response = restaurantService.register(request);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("테스트식당");
        assertThat(response.capacity()).isEqualTo(50);
        assertThat(response.maxWaitingLimit()).isEqualTo(10);

        then(restaurantRepository).should(times(1)).save(any(Restaurant.class));
        then(redisTemplate).should(times(1)).opsForValue();
        then(valueOperations).should(times(1)).set(eq(KEY_PREFIX + 1L), eq("테스트식당"), eq(CACHE_TTL));
    }

    @DisplayName("식당_단건_조회_성공")
    @Test
    void 식당_단건_조회_성공() {
        // given
        Long restaurantId = 1L;
        Restaurant restaurant = Restaurant.create("테스트식당", 50, 10);
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);

        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.of(restaurant));

        // when
        RestaurantResponse response = restaurantService.getRestaurant(restaurantId);

        // then
        assertThat(response.id()).isEqualTo(restaurantId);
        assertThat(response.name()).isEqualTo("테스트식당");
        assertThat(response.capacity()).isEqualTo(50);
        assertThat(response.maxWaitingLimit()).isEqualTo(10);

        then(restaurantRepository).should(times(1)).findById(restaurantId);
    }

    @DisplayName("식당_단건_조회_실패_존재하지_않는_식당")
    @Test
    void 식당_단건_조회_실패_존재하지_않는_식당() {
        // given
        Long restaurantId = 99L;
        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> restaurantService.getRestaurant(restaurantId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + restaurantId);

        then(restaurantRepository).should(times(1)).findById(restaurantId);
    }

    @DisplayName("식당_정보_수정_성공")
    @Test
    void 식당_정보_수정_성공() {
        // given
        Long restaurantId = 1L;
        Restaurant restaurant = Restaurant.create("기존식당", 50, 10);
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);

        UpdateRestaurantRequest request = new UpdateRestaurantRequest("새로운식당", 60, 15);

        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.of(restaurant));

        // when
        RestaurantResponse response = restaurantService.update(restaurantId, request);

        // then
        assertThat(response.id()).isEqualTo(restaurantId);
        assertThat(response.name()).isEqualTo("새로운식당");
        assertThat(response.capacity()).isEqualTo(60);
        assertThat(response.maxWaitingLimit()).isEqualTo(15);

        // Dirty Checking으로 인해 Restaurant 객체의 상태가 변경되었는지 확인
        assertThat(restaurant.getName()).isEqualTo("새로운식당");
        assertThat(restaurant.getCapacity()).isEqualTo(60);
        assertThat(restaurant.getMaxWaitingLimit()).isEqualTo(15);

        then(restaurantRepository).should(times(1)).findById(restaurantId);
        then(redisTemplate).should(times(1)).opsForValue();
        then(valueOperations).should(times(1)).set(eq(KEY_PREFIX + restaurantId), eq("새로운식당"), eq(CACHE_TTL));
    }

    @DisplayName("식당_정보_수정_실패_존재하지_않는_식당")
    @Test
    void 식당_정보_수정_실패_존재하지_않는_식당() {
        // given
        Long restaurantId = 99L;
        UpdateRestaurantRequest request = new UpdateRestaurantRequest("새로운식당", 60, 15);

        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> restaurantService.update(restaurantId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + restaurantId);

        then(restaurantRepository).should(times(1)).findById(restaurantId);
        then(redisTemplate).shouldHaveNoInteractions(); // 식당을 찾지 못하면 Redis와 상호작용 없음
    }

    @DisplayName("식당_영업_상태_OPEN_성공")
    @Test
    void 식당_영업_상태_OPEN_성공() {
        // given
        Long restaurantId = 1L;
        Restaurant restaurant = Restaurant.create("테스트식당", 50, 10);
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        restaurant.close(); // 초기 상태를 CLOSED로 설정

        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.of(restaurant));

        // when
        restaurantService.open(restaurantId);

        // then
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.OPEN); // 상태 변경 확인
        then(restaurantRepository).should(times(1)).findById(restaurantId);
    }

    @DisplayName("식당_영업_상태_OPEN_실패_존재하지_않는_식당")
    @Test
    void 식당_영업_상태_OPEN_실패_존재하지_않는_식당() {
        // given
        Long restaurantId = 99L;
        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> restaurantService.open(restaurantId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + restaurantId);

        then(restaurantRepository).should(times(1)).findById(restaurantId);
    }

    @DisplayName("식당_영업_상태_CLOSE_성공")
    @Test
    void 식당_영업_상태_CLOSE_성공() {
        // given
        Long restaurantId = 1L;
        Restaurant restaurant = Restaurant.create("테스트식당", 50, 10);
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        restaurant.open(); // 초기 상태를 OPEN으로 설정

        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.of(restaurant));

        // when
        restaurantService.close(restaurantId);

        // then
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.CLOSED); // 상태 변경 확인
        then(restaurantRepository).should(times(1)).findById(restaurantId);
    }

    @DisplayName("식당_영업_상태_CLOSE_실패_존재하지_않는_식당")
    @Test
    void 식당_영업_상태_CLOSE_실패_존재하지_않는_식당() {
        // given
        Long restaurantId = 99L;
        given(restaurantRepository.findById(restaurantId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> restaurantService.close(restaurantId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("식당을 찾을 수 없습니다. ID: " + restaurantId);

        then(restaurantRepository).should(times(1)).findById(restaurantId);
    }

    @DisplayName("손님_입장_성공")
    @Test
    void 손님_입장_성공() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        given(restaurantRepository.increaseOccupancy(restaurantId, partySize)).willReturn(1); // 1개의 행이 업데이트됨 (성공)

        // when
        restaurantService.enter(restaurantId, partySize);

        // then
        then(restaurantRepository).should(times(1)).increaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("손님_입장_실패_만석")
    @Test
    void 손님_입장_실패_만석() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        given(restaurantRepository.increaseOccupancy(restaurantId, partySize)).willReturn(0); // 0개의 행이 업데이트됨 (실패)

        // when & then
        assertThatThrownBy(() -> restaurantService.enter(restaurantId, partySize))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("만석이라 입장할 수 없습니다.");

        then(restaurantRepository).should(times(1)).increaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("손님_퇴장_성공")
    @Test
    void 손님_퇴장_성공() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        given(restaurantRepository.decreaseOccupancy(restaurantId, partySize)).willReturn(1); // 1개의 행이 업데이트됨 (성공)

        // when
        restaurantService.exit(restaurantId, partySize);

        // then
        then(restaurantRepository).should(times(1)).decreaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("손님_퇴장_실패_현재_입장_중인_손님_없음")
    @Test
    void 손님_퇴장_실패_현재_입장_중인_손님_없음() {
        // given
        Long restaurantId = 1L;
        int partySize = 2;
        given(restaurantRepository.decreaseOccupancy(restaurantId, partySize)).willReturn(0); // 0개의 행이 업데이트됨 (실패)

        // when & then
        assertThatThrownBy(() -> restaurantService.exit(restaurantId, partySize))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("현재 입장 중인 손님이 없습니다.");

        then(restaurantRepository).should(times(1)).decreaseOccupancy(restaurantId, partySize);
    }

    @DisplayName("모든_식당_조회_성공")
    @Test
    void 모든_식당_조회_성공() {
        // given
        Restaurant restaurant1 = Restaurant.create("식당1", 30, 5);
        ReflectionTestUtils.setField(restaurant1, "id", 1L);
        Restaurant restaurant2 = Restaurant.create("식당2", 40, 8);
        ReflectionTestUtils.setField(restaurant2, "id", 2L);

        List<Restaurant> restaurants = Arrays.asList(restaurant1, restaurant2);
        // findAll 호출 시 Sort.by(Sort.Direction.DESC, "id") 인자가 전달되는지 확인
        given(restaurantRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))).willReturn(restaurants);

        // when
        List<RestaurantResponse> responses = restaurantService.getAllRestaurants();

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).name()).isEqualTo("식당1");
        assertThat(responses.get(1).id()).isEqualTo(2L);
        assertThat(responses.get(1).name()).isEqualTo("식당2");

        then(restaurantRepository).should(times(1)).findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @DisplayName("식당_삭제_성공")
    @Test
    void 식당_삭제_성공() {
        // given
        Long restaurantId = 1L;

        // when
        restaurantService.deleteRestaurant(restaurantId);

        // then
        then(restaurantRepository).should(times(1)).deleteById(restaurantId);
        then(redisTemplate).should(times(1)).delete(eq(KEY_PREFIX + restaurantId));
    }
}