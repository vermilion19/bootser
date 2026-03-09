package com.booster.kotlin.boardservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@EnableCaching
@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType(Any::class.java)
                    .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
            )
        }
        // deprecated API 없이 RedisSerializer 직접 구현
        val valueSerializer = object : RedisSerializer<Any> {
            override fun serialize(value: Any?): ByteArray =
                if (value == null) ByteArray(0) else objectMapper.writeValueAsBytes(value)

            override fun deserialize(bytes: ByteArray?): Any? =
                if (bytes == null || bytes.isEmpty()) null
                else objectMapper.readValue(bytes, Any::class.java)
        }

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
            )
            .disableCachingNullValues()

        val cacheConfigs = mapOf(
            CacheNames.COMMENTS to defaultConfig.entryTtl(Duration.ofMinutes(10)),
            CacheNames.REPLIES  to defaultConfig.entryTtl(Duration.ofMinutes(10)),
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build()
    }
}

object CacheNames {
    const val COMMENTS = "comments"
    const val REPLIES  = "replies"
}
