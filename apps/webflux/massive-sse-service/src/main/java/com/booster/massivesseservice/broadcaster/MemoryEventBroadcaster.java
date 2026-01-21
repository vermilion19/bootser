package com.booster.massivesseservice.broadcaster;

import com.booster.massivesseservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.broadcaster.type", havingValue = "memory", matchIfMissing = true)
public class MemoryEventBroadcaster implements EventBroadcaster {

    private final NotificationService notificationService;

    @Override
    public void broadcast(String message) {
        // 그냥 바로 로컬 유저에게 쏨
        notificationService.sendToLocalConnectionUsers(message);
    }
}
