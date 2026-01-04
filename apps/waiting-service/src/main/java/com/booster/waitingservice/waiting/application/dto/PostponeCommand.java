package com.booster.waitingservice.waiting.application.dto;

import jakarta.validation.constraints.NotNull;

public record PostponeCommand(
        @NotNull Long waitingId,
        @NotNull Long restaurantId) {
}
