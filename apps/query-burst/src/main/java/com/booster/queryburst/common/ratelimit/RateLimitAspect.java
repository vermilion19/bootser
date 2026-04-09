package com.booster.queryburst.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "RATE:";
    private static final String ABUSE_DETECTION_TOPIC = "abuse-detection";

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
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(rateLimitAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedRateLimit rateLimitAnnotation) throws Throwable {
        int permits = rateLimitAnnotation.permits();
        int windowSeconds = rateLimitAnnotation.windowSeconds();
        validateConfiguration(permits, windowSeconds);

        String resolvedKey = resolveKey(joinPoint, rateLimitAnnotation.key());
        if (!tryAcquire(resolvedKey, permits, windowSeconds)) {
            log.warn("[RateLimit] request rejected. key={}, permits={}/{}", resolvedKey, 0, permits);
            publishAbuseEvent(resolvedKey, permits, windowSeconds);
            throw new RateLimitExceededException(resolvedKey);
        }

        log.debug("[RateLimit] request allowed. key={}", resolvedKey);
        return joinPoint.proceed();
    }

    private boolean tryAcquire(String resolvedKey, int permits, int windowSeconds) {
        String redisKey = KEY_PREFIX + resolvedKey;
        double refillRate = (double) permits / windowSeconds;
        long now = Instant.now().toEpochMilli();
        int ttl = windowSeconds * 2;

        try {
            return executeTokenBucket(redisKey, now, permits, refillRate, ttl);
        } catch (Exception e) {
            log.error("[RateLimit] Redis unavailable. Allowing request. key={}, reason={}",
                    resolvedKey, e.getMessage());
            return true;
        }
    }

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

    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);

        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        } else {
            for (int i = 0; i < args.length; i++) {
                context.setVariable("arg" + i, args[i]);
                context.setVariable("p" + i, args[i]);
            }
        }

        Object value = spelParser.parseExpression(keyExpression).getValue(context);
        return value != null ? value.toString() : "unknown";
    }

    private void validateConfiguration(int permits, int windowSeconds) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be greater than 0");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be greater than 0");
        }
    }

    private void publishAbuseEvent(String key, int permits, int windowSeconds) {
        try {
            String message = """
                    {"key":"%s","permits":%d,"windowSeconds":%d,"timestamp":%d}
                    """.formatted(key, permits, windowSeconds, Instant.now().toEpochMilli()).strip();
            kafkaTemplate.send(ABUSE_DETECTION_TOPIC, key, message);
        } catch (Exception e) {
            log.warn("[RateLimit] abuse event publish failed. key={}, reason={}", key, e.getMessage());
        }
    }
}
