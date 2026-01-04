package com.booster.waitingservice.waiting.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record PostponeRequest(
        @NotNull(message = "식당 ID는 필수입니다.")
        Long restaurantId) {
}
