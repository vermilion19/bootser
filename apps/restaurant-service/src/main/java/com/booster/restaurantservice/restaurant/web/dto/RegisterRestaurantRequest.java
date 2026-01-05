package com.booster.restaurantservice.restaurant.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRestaurantRequest(
        @NotBlank(message = "식당 이름은 필수입니다.")
        String name,

        @NotNull(message = "수용 인원은 필수입니다.")
        @Min(1)
        Integer capacity,

        @NotNull(message = "대기 제한 수는 필수입니다.")
        @Min(1)
        Integer maxWaitingLimit
) {
}
