package com.booster.waitingservice.waiting.infastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "restaurant-service", url = "${app.feign.restaurant.url}")
public interface RestaurantClient {

    // 식당 서비스의 GET /api/restaurants/{id} 를 호출한다고 가정
    @GetMapping("/api/restaurants/{restaurantId}")
    RestaurantResponse getRestaurant(@PathVariable("restaurantId") Long restaurantId);

    // 응답 DTO (내부 static record로 정의하거나 별도 클래스로 분리)
    record RestaurantResponse(Long id, String name, String address) {}
}
