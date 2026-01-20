package com.booster.coinservice.config;

import com.booster.coinservice.CoinSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@RequiredArgsConstructor
public class RedisSubscriberConfig {

    private final CoinSseService coinSseService;

    // 1. 리스너 어댑터: Redis 메시지가 오면 처리할 메서드 지정
    @Bean
    public MessageListenerAdapter messageListener() {
        // Redis 메시지가 오면 CoinSseService의 'broadcast' 메서드를 실행해라!
        return new MessageListenerAdapter(coinSseService, "broadcast");
    }

    // 2. 리스너 컨테이너: 실제 구독 연결 관리
    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // "coin-trade-topic" 채널을 구독하고, 메시지가 오면 listenerAdapter에게 넘김
        container.addMessageListener(listenerAdapter, new ChannelTopic("coin-trade-topic"));

        return container;
    }

}
