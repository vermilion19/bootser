package com.booster.massivesseservice.broadcaster;

public interface EventBroadcaster {
    /**
     * 메시지를 전파한다. (메모리 방식 or Redis 방식)
     */
    void broadcast(String message);
}
