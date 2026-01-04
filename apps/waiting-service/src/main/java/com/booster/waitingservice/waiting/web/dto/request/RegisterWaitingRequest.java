package com.booster.waitingservice.waiting.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterWaitingRequest(
        @NotNull(message = "식당 id는 필수입니다")
        Long restaurantId,

        @NotBlank(message = "전화번호는 필수입니다.")
        String guestPhone,

        @Min(value = 1 ,message = "일행 수는 최소 1명 이상이어야 합니다.")
        int partySize
) {
}
