package com.booster.kotlin.shoppingservice.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories

@Configuration
@EnableRedisRepositories(basePackages = ["com.booster.kotlin.shoppingservice.auth"])
class RedisConfig {
}