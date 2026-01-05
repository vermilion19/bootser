package com.booster.restaurantservice.restaurant.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.restaurantservice.restaurant.application.RestaurantService;
import com.booster.restaurantservice.restaurant.web.dto.RegisterRestaurantRequest;
import com.booster.restaurantservice.restaurant.web.dto.RestaurantResponse;
import com.booster.restaurantservice.restaurant.web.dto.UpdateRestaurantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    // 1. 식당 등록
    @PostMapping
    public ApiResponse<RestaurantResponse> register(@Valid @RequestBody RegisterRestaurantRequest request) {
        return ApiResponse.success(restaurantService.register(request));
    }

    // 2. 식당 조회
    @GetMapping("/{restaurantId}")
    public ApiResponse<RestaurantResponse> getRestaurant(@PathVariable Long restaurantId) {
        return ApiResponse.success(restaurantService.getRestaurant(restaurantId));
    }

    // 3. 식당 정보 수정
    @PatchMapping("/{restaurantId}")
    public ApiResponse<RestaurantResponse> update(
            @PathVariable Long restaurantId,
            @Valid @RequestBody UpdateRestaurantRequest request
    ) {
        return ApiResponse.success(restaurantService.update(restaurantId, request));
    }

    // 4. 영업 시작
    @PostMapping("/{restaurantId}/open")
    public ApiResponse<Void> open(@PathVariable Long restaurantId) {
        restaurantService.open(restaurantId);
        return ApiResponse.success();
    }

    // 5. 영업 종료
    @PostMapping("/{restaurantId}/close")
    public ApiResponse<Void> close(@PathVariable Long restaurantId) {
        restaurantService.close(restaurantId);
        return ApiResponse.success();
    }

    // 6. 입장 처리
    @PostMapping("/{restaurantId}/entry")
    public ApiResponse<Void> entry(@PathVariable Long restaurantId) {
        restaurantService.enter(restaurantId);
        return ApiResponse.success();
    }

    // 7. 퇴장 처리
    @PostMapping("/{restaurantId}/exit")
    public ApiResponse<Void> exit(@PathVariable Long restaurantId) {
        restaurantService.exit(restaurantId);
        return ApiResponse.success();
    }

}
