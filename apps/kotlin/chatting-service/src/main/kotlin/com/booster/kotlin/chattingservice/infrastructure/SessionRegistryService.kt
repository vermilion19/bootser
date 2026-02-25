package com.booster.kotlin.chattingservice.infrastructure

import com.booster.kotlin.core.logger
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Redis Hash 기반 세션 레지스트리.
 *
 * Key:   session:registry
 * Field: userId
 * Value: instanceId (어느 서버에 연결되어 있는지)
 *
 * 용도:
 * - 특정 유저에게 직접 메시지를 라우팅할 때 instanceId 조회
 * - 모니터링: 전체 연결된 유저 목록, 서버별 분포 파악
 */
@Component
class SessionRegistryService(
    private val redisTemplate: ReactiveStringRedisTemplate
) {
    private val log = logger()

    companion object {
        const val KEY = "session:registry"
    }

    fun register(userId: String, instanceId: String) {
        redisTemplate.opsForHash<String, String>()
            .put(KEY, userId, instanceId)
            .doOnError { e -> log.error("[SESSION] register failed: userId={}, {}", userId, e.message) }
            .subscribe()
    }

    fun unregister(userId: String) {
        redisTemplate.opsForHash<String, String>()
            .remove(KEY, userId)
            .doOnError { e -> log.error("[SESSION] unregister failed: userId={}, {}", userId, e.message) }
            .subscribe()
    }
}
