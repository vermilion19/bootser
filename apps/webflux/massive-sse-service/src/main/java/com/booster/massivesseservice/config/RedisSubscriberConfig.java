package com.booster.massivesseservice.config;

import com.booster.massivesseservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.broadcaster.type", havingValue = "redis")
public class RedisSubscriberConfig {
    private final NotificationService notificationService;
    private static final String CHANNEL_NAME = "sse-chat-channel";

    @Bean
    public ReactiveRedisMessageListenerContainer redisMessageListenerContainer(
            ReactiveRedisConnectionFactory factory) {

        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(factory);

        // 1. 특정 채널(sse-chat-channel)을 구독
        container.receive(ChannelTopic.of(CHANNEL_NAME))
                // 2. 메시지가 오면 처리하는 로직
                .map(message -> message.getMessage()) // 실제 데이터(String)만 추출
                .doOnNext(msg -> {
                    log.info("Received from Redis: {}", msg);
                    // 3. 로컬에 붙은 유저들에게 최종 발송
                    notificationService.sendToLocalConnectionUsers(msg);
                })
                .subscribe(); // 스트림 가동

        return container;
    }

}
