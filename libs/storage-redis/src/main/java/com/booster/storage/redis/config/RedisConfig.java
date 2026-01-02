package com.booster.storage.redis.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // 1. Jackson 3 방식: Builder 패턴으로 ObjectMapper 생성
        ObjectMapper objectMapper = JsonMapper.builder()
                // 1-1. 모듈 추가 (registerModule 대신 addModule 사용)
                .addModule(new JavaTimeModule())
                // 1-2. 타입 정보 저장 설정 (activateDefaultTyping 메서드가 빌더 안에 있음)
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build(),
                        ObjectMapper.DefaultTyping.NON_FINAL
                )
                .build(); // 빌드 끝

        // 2. Serializer 생성
        GenericJacksonJsonRedisSerializer valueSerializer = new GenericJacksonJsonRedisSerializer(objectMapper);

        // 3. RedisTemplate 설정
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);

        return template;
    }
}