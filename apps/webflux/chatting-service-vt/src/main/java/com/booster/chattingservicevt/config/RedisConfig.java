package com.booster.chattingservicevt.config;

import com.booster.chattingservicevt.dto.ChatMessage;
import com.booster.chattingservicevt.service.ChatServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final ChatServiceImpl chatService;
    private final ObjectMapper objectMapper;

    private static final String REDIS_TOPIC = "chat.public";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, ChannelTopic.of(REDIS_TOPIC));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter() {
        return new MessageListenerAdapter(new RedisMessageSubscriber());
    }

    /**
     * Redis 메시지 수신 핸들러
     * Virtual Thread에서 실행됨
     */
    public class RedisMessageSubscriber {

        @SuppressWarnings("unused") // MessageListenerAdapter가 리플렉션으로 호출
        public void handleMessage(String message) {
            try {
                ChatMessage chatMessage = objectMapper.readValue(message, ChatMessage.class);
                chatService.broadcastToLocalUsers(chatMessage);
            } catch (Exception e) {
                log.error("Failed to process Redis message: {}", message, e);
            }
        }
    }
}
