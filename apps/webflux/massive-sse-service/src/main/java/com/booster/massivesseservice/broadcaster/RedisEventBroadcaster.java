package com.booster.massivesseservice.broadcaster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.broadcaster.type", havingValue = "redis")
public class RedisEventBroadcaster implements EventBroadcaster {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String CHANNEL_NAME = "sse-chat-channel";

    @Override
    public void broadcast(String message) {
        log.info("Publishing to Redis channel: {}", CHANNEL_NAME);
        // convertAndSend: Redis Pub/Sub으로 메시지 발행
        redisTemplate.convertAndSend(CHANNEL_NAME, message)
                .subscribe(); // 구독해야 실제 전송됨
    }
}
