package com.booster.restaurantservice.restaurant.web.dto;

import com.booster.restaurantservice.restaurant.domain.Restaurant;
import com.booster.restaurantservice.restaurant.domain.RestaurantStatus;

public record RestaurantResponse(
        Long id,
        String name,
        int capacity,
        int currentOccupancy,
        int maxWaitingLimit,
        RestaurantStatus status
) {
    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getCapacity(),
                restaurant.getCurrentOccupancy(),
                restaurant.getMaxWaitingLimit(),
                restaurant.getStatus()
        );
    }
}
