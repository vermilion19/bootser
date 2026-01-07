package com.booster.core.web.event;

public record WaitingEvent(
        Long restaurantId,
        String restaurantName,
        Long waitingId,
        String guestPhone,
        int waitingNumber,
        Long rank,
        int partySize,
        EventType type
) {
    public enum EventType {
        REGISTER, ENTER, CANCEL,CALLED
    }

    public static WaitingEvent of(Long restaurantId,String restaurantName, Long waitingId, String guestPhone, int waitingNumber, Long rank,int partySize, EventType type) {
        return new WaitingEvent(restaurantId,restaurantName, waitingId, guestPhone, waitingNumber, rank,partySize, type);
    }
}
