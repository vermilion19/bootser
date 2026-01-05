package com.booster.restaurantservice.restaurant.web.dto;

import jakarta.validation.constraints.Min;

public record UpdateRestaurantRequest(
        String name,
        @Min(1) Integer capacity,
        @Min(1) Integer maxWaitingLimit
) {
}
