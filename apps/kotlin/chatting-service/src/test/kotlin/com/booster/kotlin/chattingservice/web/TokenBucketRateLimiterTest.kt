package com.booster.kotlin.chattingservice.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TokenBucketRateLimiterTest {

    @Nested
    inner class BurstAllowance {

        @Test
        fun `초기 토큰 범위 내 연속 요청은 모두 허용된다`() {
            val limiter = TokenBucketRateLimiter(maxTokens = 5, refillPerSecond = 1)

            val results = (1..5).map { limiter.tryConsume() }

            assertThat(results).containsOnly(true)
        }

        @Test
        fun `토큰 소진 후 요청은 거부된다`() {
            val limiter = TokenBucketRateLimiter(maxTokens = 3, refillPerSecond = 1)

            repeat(3) { limiter.tryConsume() }

            assertThat(limiter.tryConsume()).isFalse()
        }

        @Test
        fun `maxTokens=1이면 첫 요청만 허용되고 이후는 거부된다`() {
            val limiter = TokenBucketRateLimiter(maxTokens = 1, refillPerSecond = 1)

            assertThat(limiter.tryConsume()).isTrue()
            assertThat(limiter.tryConsume()).isFalse()
        }
    }

    @Nested
    inner class Refill {

        @Test
        fun `토큰 소진 후 충분한 시간이 지나면 토큰이 보충된다`() {
            val limiter = TokenBucketRateLimiter(maxTokens = 5, refillPerSecond = 10)

            repeat(5) { limiter.tryConsume() }
            assertThat(limiter.tryConsume()).isFalse()

            Thread.sleep(200) // 200ms → 10 tokens/sec → 2개 보충 예상

            assertThat(limiter.tryConsume()).isTrue()
        }

        @Test
        fun `보충된 토큰은 maxTokens를 초과하지 않는다`() {
            // refillPerSecond=2 → 1 token per 500ms
            // 연속 호출 간격(~µs)에서 추가 토큰이 생성되지 않아 cap 검증이 안정적
            val limiter = TokenBucketRateLimiter(maxTokens = 3, refillPerSecond = 2)

            // 전부 소진
            repeat(3) { limiter.tryConsume() }

            // 2 tokens/sec * 2s = 4 tokens → maxTokens=3 cap
            Thread.sleep(2000)

            // maxTokens만큼만 성공해야 함
            val results = (1..3).map { limiter.tryConsume() }
            assertThat(results).containsOnly(true)
            // 500ms 안에 추가 토큰 생성 불가 → 4번째 호출은 거부
            assertThat(limiter.tryConsume()).isFalse()
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `refillPerSecond=0이면 토큰이 보충되지 않는다`() {
            val limiter = TokenBucketRateLimiter(maxTokens = 2, refillPerSecond = 0)

            repeat(2) { limiter.tryConsume() }
            Thread.sleep(100)

            assertThat(limiter.tryConsume()).isFalse()
        }

        @Test
        fun `첫 호출은 항상 허용된다 (maxTokens=1)`() {
            val limiter = TokenBucketRateLimiter(maxTokens = 1, refillPerSecond = 0)

            assertThat(limiter.tryConsume()).isTrue()
        }
    }
}
