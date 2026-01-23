package com.booster.chattingservice.config;

import com.booster.chattingservice.dto.ChatMessage;
import com.booster.chattingservice.service.ChatServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final ChatServiceImpl chatService; // 구체 클래스 주입 (broadcastToLocalUsers 호출용)
    private final ObjectMapper objectMapper;

    @Bean
    public ReactiveRedisMessageListenerContainer redisMessageListenerContainer(
            ReactiveRedisConnectionFactory factory) {

        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(factory);

        // "chat.public" 토픽을 구독
        container.receive(ChannelTopic.of("chat.public"))
                .map(record -> record.getMessage()) // 메시지 내용(String) 추출
                .doOnNext(json -> {
                    try {
                        // JSON -> Record 변환
                        ChatMessage message = objectMapper.readValue(json, ChatMessage.class);
                        // 서비스 계층으로 전달 -> 로컬 유저들에게 발송
                        chatService.broadcastToLocalUsers(message);
                    } catch (Exception e) {
                        log.error("Failed to process Redis message", e);
                    }
                })
                .subscribe(); // 중요: 스트림 구독 시작

        return container;
    }
}
