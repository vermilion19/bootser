package com.booster.queryburst.common.ratelimit;

/**
 * 분산 Rate Limit 초과 예외.
 *
 * HTTP 429 Too Many Requests로 변환된다.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String rateLimitKey;

    public RateLimitExceededException(String rateLimitKey) {
        super("Rate limit exceeded. key=" + rateLimitKey);
        this.rateLimitKey = rateLimitKey;
    }

    public String getRateLimitKey() {
        return rateLimitKey;
    }
}
