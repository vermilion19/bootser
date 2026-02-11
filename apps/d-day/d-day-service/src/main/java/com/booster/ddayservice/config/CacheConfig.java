package com.booster.ddayservice.config;

import com.booster.common.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        ObjectMapper objectMapper = JsonUtils.MAPPER_FOR_REDIS;

        GenericJacksonJsonRedisSerializer valueSerializer = new GenericJacksonJsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(30))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        Map<String, RedisCacheConfiguration> customConfigs = new HashMap<>();
        // 외부 API 응답 캐시
        customConfigs.put("external-holidays", defaultConfig.entryTtl(Duration.ofDays(30)));
        customConfigs.put("external-movies", defaultConfig.entryTtl(Duration.ofDays(30)));
        customConfigs.put("external-sports", defaultConfig.entryTtl(Duration.ofDays(30)));

        // 공개 데이터 조회 캐시 (카테고리별 TTL)
        customConfigs.put("public-holidays", defaultConfig.entryTtl(Duration.ofDays(180)));
        customConfigs.put("public-entertainment", defaultConfig.entryTtl(Duration.ofDays(30)));
        customConfigs.put("public-others", defaultConfig.entryTtl(Duration.ofDays(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(customConfigs)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("캐시 조회 실패 - cache={}, key={}, error={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("캐시 저장 실패 - cache={}, key={}, error={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("캐시 삭제 실패 - cache={}, key={}, error={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("캐시 초기화 실패 - cache={}, error={}", cache.getName(), e.getMessage());
            }
        };
    }
}
