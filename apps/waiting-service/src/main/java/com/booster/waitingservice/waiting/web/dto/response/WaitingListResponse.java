package com.booster.waitingservice.waiting.web.dto.response;

import com.booster.waitingservice.waiting.domain.Waiting;
import com.booster.waitingservice.waiting.domain.WaitingStatus;

import java.time.LocalDateTime;

public record WaitingListResponse(
        Long id,
        Long restaurantId,
        String guestPhone,
        int waitingNumber,
        int partySize,
        WaitingStatus status,
        LocalDateTime createdAt
) {
    public static WaitingListResponse from(Waiting waiting) {
        return new WaitingListResponse(
                waiting.getId(),
                waiting.getRestaurantId(),
                waiting.getGuestPhone(),
                waiting.getWaitingNumber(),
                waiting.getPartySize(),
                waiting.getStatus(),
                waiting.getCreatedAt()
        );
    }
}
