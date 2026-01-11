package com.booster.waitingservice.waiting.application.dto;

public record WaitingRegisteredEvent(
        Long waitingId,
        Long restaurantId,
        String restaurantName,
        String phoneNumber,
        Integer waitingNumber,
        Integer partySize
) {
}
