package com.booster.core.web.event;

public record WaitingEvent(
        Long restaurantId,
        Long waitingId,
        String guestPhone,
        int waitingNumber,
        Long rank,
        EventType type
) {
    public enum EventType {
        REGISTER, ENTER, CANCEL
    }

    public static WaitingEvent of(Long restaurantId, Long waitingId, String guestPhone, int waitingNumber, Long rank, EventType type) {
        return new WaitingEvent(restaurantId, waitingId, guestPhone, waitingNumber, rank, type);
    }
}
