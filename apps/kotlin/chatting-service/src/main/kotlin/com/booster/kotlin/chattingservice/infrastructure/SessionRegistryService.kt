package com.booster.kotlin.chattingservice.infrastructure

import com.booster.kotlin.core.logger
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Redis String 기반 세션 레지스트리.
 *
 * Key:   session:user:{userId}
 * Value: instanceId (어느 서버에 연결되어 있는지)
 * TTL:   SESSION_TTL (기본 1시간)
 *
 * 용도:
 * - 특정 유저에게 직접 메시지를 라우팅할 때 instanceId 조회
 * - 모니터링: 전체 연결된 유저 목록, 서버별 분포 파악
 *
 * Hash → String+TTL 전환 이유:
 * - Redis Hash는 필드 단위 TTL 미지원 (Redis 7.4 미만)
 * - 서버 크래시 시 unregister 미호출 → 죽은 세션 영구 잔류 문제 해결
 * - 개별 String 키에 TTL을 부여하면 크래시 후 SESSION_TTL 내 자동 만료
 */
@Component
class SessionRegistryService(
    private val redisTemplate: ReactiveStringRedisTemplate
) {
    private val log = logger()

    companion object {
        const val KEY_PREFIX = "session:user:"
        val SESSION_TTL: Duration = Duration.ofHours(1)
    }

    fun register(userId: String, instanceId: String) {
        redisTemplate.opsForValue()
            .set(KEY_PREFIX + userId, instanceId, SESSION_TTL)
            .doOnError { e -> log.error("[SESSION] register failed: userId={}, {}", userId, e.message) }
            .subscribe()
    }

    fun unregister(userId: String) {
        redisTemplate.delete(KEY_PREFIX + userId)
            .doOnError { e -> log.error("[SESSION] unregister failed: userId={}, {}", userId, e.message) }
            .subscribe()
    }
}