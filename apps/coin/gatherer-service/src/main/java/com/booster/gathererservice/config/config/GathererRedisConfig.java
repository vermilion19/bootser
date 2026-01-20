package com.booster.gathererservice.config.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;

@Configuration
public class GathererRedisConfig {
    @Bean
    public ChannelTopic coinTopic() {
        return new ChannelTopic("coin-trade-topic");
    }

}
