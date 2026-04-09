package com.booster.queryburst.common.ratelimit;

import java.lang.annotation.*;

/**
 * 분산 Rate Limiter 어노테이션.
 *
 * <h2>동작</h2>
 * Redis Token Bucket 알고리즘으로 요청 빈도를 제한한다.
 * 단일 인스턴스뿐 아니라 Scale-out 환경에서도 정확한 제한이 적용된다.
 *
 * <h2>사용 예시</h2>
 * <pre>
 * @DistributedRateLimit(key = "'order:' + #request.memberId()", permits = 5, windowSeconds = 60)
 * public OrderResult placeOrder(OrderCreateRequest request, String idempotencyKey) { ... }
 * </pre>
 *
 * <h2>key (SpEL 표현식)</h2>
 * 메서드 파라미터를 #paramName 형태로 참조할 수 있다.
 * 반환된 문자열이 Rate Limit 버킷의 식별자가 된다.
 *
 * <h2>Redis 키 구조</h2>
 * {@code RATE:{resolvedKey}} — Hash(tokens, lastRefill), TTL 자동 관리
 *
 * <h2>초과 시 동작</h2>
 * {@link RateLimitExceededException} 발생 (HTTP 429)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedRateLimit {

    /**
     * Rate Limit 버킷을 식별하는 SpEL 표현식.
     * 메서드 파라미터를 #paramName으로 참조 가능.
     * 예: "'order:' + #request.memberId()"
     */
    String key();

    /**
     * 윈도우(windowSeconds) 내 최대 허용 요청 수 (burst capacity).
     */
    int permits() default 10;

    /**
     * 토큰 리필 윈도우 (초). permits개의 토큰이 이 시간 동안 순차 리필된다.
     * 리필 속도 = permits / windowSeconds (토큰/초)
     */
    int windowSeconds() default 60;
}
