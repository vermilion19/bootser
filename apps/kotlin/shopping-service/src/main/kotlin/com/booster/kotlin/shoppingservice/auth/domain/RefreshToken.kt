package com.booster.kotlin.shoppingservice.auth.domain

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive

@RedisHash("refresh_token")
data class RefreshToken(
    @Id val userId: Long,
    val token: String,
    @TimeToLive val ttl: Long,
    ) {
}