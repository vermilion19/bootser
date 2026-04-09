package com.booster.queryburst.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.List;

/**
 * 분산 Rate Limiter AOP Aspect.
 *
 * <h2>알고리즘: Token Bucket</h2>
 * <pre>
 * Redis Hash (key = "RATE:{resolvedKey}"):
 *   tokens    → 현재 잔여 토큰 수 (float)
 *   lastRefill → 마지막 리필 시각 (Unix ms)
 *
 * 요청마다:
 *   1. elapsed = now - lastRefill (초)
 *   2. tokens = MIN(maxTokens, tokens + elapsed * refillRate)
 *   3. tokens >= 1 → 토큰 차감 → ALLOWED
 *      tokens < 1  → REJECTED (429)
 * </pre>
 *
 * <h2>Lua Script 사용 이유</h2>
 * HMGET → 계산 → HSET 과정이 원자적으로 실행되어야 Race Condition이 없다.
 * Redis Lua Script는 단일 스레드로 실행되므로 별도 락 없이 원자성이 보장된다.
 *
 * <h2>어뷰징 탐지</h2>
 * Rate Limit 초과 시 Kafka "abuse-detection" 토픽에 이벤트를 발행한다.
 * 비동기 fire-and-forget이며, Kafka 장애 시 로그만 남기고 Rate Limit 응답은 그대로 반환한다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "RATE:";
    private static final String ABUSE_DETECTION_TOPIC = "abuse-detection";

    /**
     * Token Bucket Lua Script.
     *
     * KEYS[1]: Redis Hash 키
     * ARGV[1]: 현재 시각 (Unix ms)
     * ARGV[2]: 최대 토큰 수 (maxTokens)
     * ARGV[3]: 초당 리필 속도 (refillRate = permits / windowSeconds)
     * ARGV[4]: 필요한 토큰 수 (항상 1)
     *
     * Returns: 1 = ALLOWED, 0 = REJECTED
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local maxTokens = tonumber(ARGV[2])
            local refillRate = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])

            local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
            local tokens = tonumber(data[1])
            local lastRefill = tonumber(data[2])

            if tokens == nil then
                tokens = maxTokens
                lastRefill = now
            end

            local elapsed = math.max(0, (now - lastRefill) / 1000)
            local newTokens = math.min(maxTokens, tokens + elapsed * refillRate)

            local allowed
            if newTokens >= requested then
                newTokens = newTokens - requested
                allowed = 1
            else
                allowed = 0
            end

            redis.call('HSET', key, 'tokens', newTokens, 'lastRefill', now)
            redis.call('EXPIRE', key, ttl)

            return allowed
            """;

    private final RedissonClient redissonClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(rateLimitAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedRateLimit rateLimitAnnotation) throws Throwable {
        String resolvedKey = resolveKey(joinPoint, rateLimitAnnotation.key());
        String redisKey = KEY_PREFIX + resolvedKey;

        int permits = rateLimitAnnotation.permits();
        int windowSeconds = rateLimitAnnotation.windowSeconds();
        double refillRate = (double) permits / windowSeconds;
        long now = Instant.now().toEpochMilli();
        // TTL = 윈도우 * 2 (버킷이 가득 찬 후 만료까지 여유 포함)
        int ttl = windowSeconds * 2;

        boolean allowed = executeTokenBucket(redisKey, now, permits, refillRate, ttl);

        if (!allowed) {
            log.warn("[RateLimit] 요청 거부. key={}, permits={}/{}", resolvedKey, 0, permits);
            publishAbuseEvent(resolvedKey, permits, windowSeconds);
            throw new RateLimitExceededException(resolvedKey);
        }

        log.debug("[RateLimit] 요청 허용. key={}", resolvedKey);
        return joinPoint.proceed();
    }

    /**
     * Lua Script를 Redis에서 실행.
     *
     * @return true = ALLOWED, false = REJECTED
     */
    private boolean executeTokenBucket(String key, long now, int maxTokens, double refillRate, int ttl) {
        RScript script = redissonClient.getScript();
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                TOKEN_BUCKET_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(key),
                String.valueOf(now),
                String.valueOf(maxTokens),
                String.valueOf(refillRate),
                "1",
                String.valueOf(ttl)
        );
        return result != null && result == 1L;
    }

    /**
     * SpEL 표현식으로 Rate Limit 키를 동적 결정.
     *
     * 메서드 파라미터를 #paramName으로 참조 가능.
     * 예: "'order:' + #request.memberId()" → "order:42"
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameters.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
        }

        Object value = spelParser.parseExpression(keyExpression).getValue(context);
        return value != null ? value.toString() : "unknown";
    }

    /**
     * 어뷰징 탐지 이벤트 Kafka 발행 (fire-and-forget).
     *
     * Kafka 장애 시 Rate Limit 응답에는 영향이 없도록 예외를 swallow한다.
     */
    private void publishAbuseEvent(String key, int permits, int windowSeconds) {
        try {
            String message = """
                    {"key":"%s","permits":%d,"windowSeconds":%d,"timestamp":%d}
                    """.formatted(key, permits, windowSeconds, Instant.now().toEpochMilli()).strip();
            kafkaTemplate.send(ABUSE_DETECTION_TOPIC, key, message);
        } catch (Exception e) {
            log.warn("[RateLimit] 어뷰징 이벤트 발행 실패. key={}, 사유: {}", key, e.getMessage());
        }
    }
}
