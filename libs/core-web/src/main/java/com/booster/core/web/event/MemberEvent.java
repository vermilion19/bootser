package com.booster.core.web.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record MemberEvent(
        String eventId,
        Long memberId,
        String nickName,
        LocalDateTime timestamp,
        EventType eventType
) {

    public enum EventType{
        SIGNIN, SIGNUP, UPDATE, DELETE
    }

    public static MemberEvent of(Long memberId,String nickName, EventType eventType) {
        return new MemberEvent(UUID.randomUUID().toString(), memberId, nickName, LocalDateTime.now(), eventType);
    }
}
