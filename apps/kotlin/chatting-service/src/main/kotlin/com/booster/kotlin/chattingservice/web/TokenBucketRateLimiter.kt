package com.booster.kotlin.chattingservice.web

/**
 * 연결당 토큰 버킷 Rate Limiter.
 *
 * 동작:
 *   - 버킷에 최대 [maxTokens]개 토큰 보유 (버스트 허용)
 *   - 초당 [refillPerSecond]개씩 토큰 보충
 *   - 토큰 있으면 소비 후 true, 없으면 false 반환
 *
 * 스레드 안전성:
 *   WebSocket 연결 1개 = Netty 이벤트 루프 1개 스레드가 전담 처리.
 *   연결당 인스턴스를 생성하므로 동기화 불필요.
 */
class TokenBucketRateLimiter(
    private val maxTokens: Int,
    private val refillPerSecond: Int,
) {
    private var tokens: Int = maxTokens
    private var lastRefillNanos: Long = System.nanoTime()

    fun tryConsume(): Boolean {
        refill()
        return if (tokens > 0) {
            tokens--
            true
        } else {
            false
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedNanos = now - lastRefillNanos
        val newTokens = (elapsedNanos * refillPerSecond / 1_000_000_000L).toInt()
        if (newTokens > 0) {
            tokens = minOf(maxTokens, tokens + newTokens)
            lastRefillNanos = now
        }
    }
}
