package com.booster.ddayservice.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * {@link LogExecutionTime} 어노테이션이 적용된 메서드의 실행 시간을 측정하는 Aspect.
 */
@Slf4j
@Aspect
@Component
public class ExecutionTimeAspect {

    /**
     * @Around: 메서드 실행 전/후를 감싸는 Advice
     *
     * @param joinPoint 현재 실행 중인 메서드 정보 (메서드명, 파라미터, 클래스 등)
     * @return 원본 메서드의 반환값
     */
    @Around("@annotation(LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // 메서드 정보 추출
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 시작 시간 기록
        long startTime = System.currentTimeMillis();

        try {
            // 원본 메서드 실행 (핵심!)
            Object result = joinPoint.proceed();

            // 실행 시간 계산
            long duration = System.currentTimeMillis() - startTime;

            // 로깅
            log.info("[{}#{}] 실행 완료 - {}ms", className, methodName, duration);

            return result;

        } catch (Throwable e) {
            // 예외 발생 시에도 실행 시간 로깅
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[{}#{}] 예외 발생 - {}ms | {}: {}",
                    className, methodName, duration,
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }
}
