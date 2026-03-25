package com.booster.waitingservice.waiting.infastructure;

import com.booster.core.web.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "restaurant-service", url = "${app.feign.restaurant.url}")
public interface RestaurantClient {

    // 기동 시 Bootstrap 전용 (런타임 조회에는 사용하지 않음)
    @GetMapping("/restaurants/v1")
    ApiResponse<List<RestaurantResponse>> getAllRestaurants();

    @GetMapping("/restaurants/v1/{restaurantId}")
    RestaurantResponse getRestaurant(@PathVariable Long restaurantId);

    record RestaurantResponse(Long id, String name, String address) {}
}
