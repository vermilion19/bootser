package com.booster.firstcomefirstserved.order.common.aop;

import com.booster.core.webflux.exception.CoreException;
import com.booster.core.webflux.exception.ErrorCode;
import com.booster.firstcomefirstserved.order.domain.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;

import static com.booster.firstcomefirstserved.order.domain.OrderStatus.PROCESSING;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        String idempotencyKey = extractIdempotencyKey(joinPoint);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                return Mono.error(e);
            }
        }

        String redisKey = "idempotency:" + idempotencyKey;
        Duration ttl = Duration.ofSeconds(idempotent.ttlSeconds());

        // [핵심 변경 1] setIfAbsent로 "선점" 시도 (Atomic)
        return redisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING.toString(), ttl)
                .flatMap(acquired -> {
                    if (Boolean.TRUE.equals(acquired)) {
                        // [CASE A] 락 획득 성공 (내가 1등) -> 로직 실행
                        return processRequest(joinPoint, redisKey, ttl);
                    } else {
                        // [CASE B] 락 획득 실패 (이미 진행 중이거나 완료됨) -> 결과 조회
                        return returnCachedResponse(redisKey);
                    }
                });
    }

    // 실제 비즈니스 로직 실행 및 결과 저장
    @SuppressWarnings("unchecked")
    private Mono<ResponseEntity<Object>> processRequest(ProceedingJoinPoint joinPoint, String redisKey, Duration ttl) {
        try {
            Mono<ResponseEntity<Object>> resultMono = (Mono<ResponseEntity<Object>>) joinPoint.proceed();

            return resultMono
                    .flatMap(entity -> {
                        if (entity.getStatusCode().is2xxSuccessful() && entity.getBody() != null) {
                            // 성공 시: PROCESSING -> 실제 JSON 결과로 덮어쓰기
                            String jsonBody = objectMapper.writeValueAsString(entity.getBody());
                            return redisTemplate.opsForValue()
                                    .set(redisKey, jsonBody, ttl)
                                    .thenReturn(entity);

                        } else {
                            // 비즈니스 실패(4xx, 5xx) 시: 키 삭제 (재시도 허용)
                            return redisTemplate.delete(redisKey).thenReturn(entity);
                        }
                    })
                    .onErrorResume(throwable -> {
                        // 에러 발생 시: 키 삭제 (중요! 안 그러면 영원히 PROCESSING 상태로 남음)
                        return redisTemplate.delete(redisKey)
                                .then(Mono.error(throwable));
                    });
        } catch (Throwable e) {
            return redisTemplate.delete(redisKey).then(Mono.error(e));
        }
    }

    // 캐시된 응답 반환 (PROCESSING 상태 처리 포함)
    private Mono<ResponseEntity<Object>> returnCachedResponse(String redisKey) {
        return redisTemplate.opsForValue().get(redisKey)
                .flatMap(val -> {
                    // 아직 처리 중이라면? (409 Conflict 발생시키는 게 정석)
                    if (PROCESSING.toString().equals(val)) {
                        return Mono.error(new CoreException(OrderErrorCode.INTERNAL_SERVER_ERROR)); // 또는 "처리 중입니다" 에러
                    }

                    // 완료된 결과라면 JSON 파싱 후 반환

                    Object cachedBody = objectMapper.readValue(val, Object.class);
                    return Mono.just(ResponseEntity.ok(cachedBody));

                });
    }



    private String extractIdempotencyKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        try {
            // 핵심: Proxy 객체가 아닌 Target 객체(실제 컨트롤러)에서 메서드를 다시 찾음
            method = joinPoint.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            log.warn("원본 메서드를 찾을 수 없음: {}", method.getName());
        }

        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];

            // 이제 어노테이션이 보일 겁니다
            RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);

            if (requestHeader != null && "Idempotency-Key".equalsIgnoreCase(requestHeader.value())) {
                Object arg = args[i];
                if (arg instanceof String) {
                    return (String) arg;
                }
                // 만약 null이면 (헤더가 안 넘어옴)
                return null;
            }
        }

        // 그래도 못 찾았으면 디버깅용 로그
        log.error("Idempotency-Key 헤더를 찾을 수 없음. Controller에 @RequestHeader가 제대로 붙어있는지 확인하세요.");
        return null;
    }
}
