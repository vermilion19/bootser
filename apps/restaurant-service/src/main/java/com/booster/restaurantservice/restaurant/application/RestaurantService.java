package com.booster.restaurantservice.restaurant.application;

import com.booster.restaurantservice.restaurant.domain.Restaurant;
import com.booster.restaurantservice.restaurant.domain.RestaurantRepository;
import com.booster.restaurantservice.restaurant.web.dto.RegisterRestaurantRequest;
import com.booster.restaurantservice.restaurant.web.dto.RestaurantResponse;
import com.booster.restaurantservice.restaurant.web.dto.UpdateRestaurantRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    // 1. 식당 등록
    public RestaurantResponse register(RegisterRestaurantRequest request) {
        Restaurant restaurant = Restaurant.create(
                request.name(),
                request.capacity(),
                request.maxWaitingLimit()
        );
        Restaurant saved = restaurantRepository.save(restaurant);
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
    public void enter(Long restaurantId) {
        // (선택) 식당 존재 여부나 영업 상태 확인이 필요하다면 여기서 가볍게 조회
        // Restaurant restaurant = findByIdOrThrow(restaurantId);
        // if (restaurant.getStatus() != RestaurantStatus.OPEN) throw ...

        // Atomic Update 실행
        int updatedRows = restaurantRepository.increaseOccupancy(restaurantId);

        if (updatedRows == 0) {
            throw new IllegalStateException("만석이라 입장할 수 없습니다.");
        }
    }

    // 6. 손님 퇴장 (Atomic)
    public void exit(Long restaurantId) {
        int updatedRows = restaurantRepository.decreaseOccupancy(restaurantId);

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

    private Restaurant findByIdOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다. ID: " + id));
    }
}
