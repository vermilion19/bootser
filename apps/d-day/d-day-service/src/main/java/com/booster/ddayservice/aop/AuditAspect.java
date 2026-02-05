package com.booster.ddayservice.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@link Auditable} 어노테이션이 적용된 메서드의 감사 로그를 기록하는 Aspect.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * @Before: 메서드 실행 전에 호출됨
     * - 요청 정보를 먼저 기록 (실패해도 누가 시도했는지 알 수 있음)
     */
    @Before("@annotation(auditable)")
    public void logBefore(JoinPoint joinPoint, Auditable auditable) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String action = auditable.action();
        Long memberId = extractMemberId(joinPoint);
        String params = formatParameters(joinPoint);

        log.info("[AUDIT] {} | {} | memberId={} | params={{}} | REQUESTED",
                timestamp, action, memberId, params);
    }

    /**
     * @AfterReturning: 메서드가 정상적으로 완료된 후 호출됨
     * - returning 속성으로 반환값에 접근 가능
     */
    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void logAfterSuccess(JoinPoint joinPoint, Auditable auditable, Object result) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String action = auditable.action();
        Long memberId = extractMemberId(joinPoint);

        log.info("[AUDIT] {} | {} | memberId={} | SUCCESS",
                timestamp, action, memberId);
    }

    /**
     * @AfterThrowing: 메서드에서 예외가 발생한 후 호출됨
     * - throwing 속성으로 예외 객체에 접근 가능
     */
    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void logAfterFailure(JoinPoint joinPoint, Auditable auditable, Throwable ex) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String action = auditable.action();
        Long memberId = extractMemberId(joinPoint);

        log.warn("[AUDIT] {} | {} | memberId={} | FAILED: {} - {}",
                timestamp, action, memberId,
                ex.getClass().getSimpleName(), ex.getMessage());
    }

    /**
     * 파라미터에서 memberId 추출
     * - 파라미터 이름이 "memberId"인 것을 찾음
     */
    private Long extractMemberId(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterNames.length; i++) {
            if ("memberId".equals(parameterNames[i]) && args[i] instanceof Long) {
                return (Long) args[i];
            }
        }
        return null;
    }

    /**
     * 파라미터를 "name=value" 형식의 문자열로 변환
     */
    private String formatParameters(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        return IntStream.range(0, parameterNames.length)
                .filter(i -> !"memberId".equals(parameterNames[i]))  // memberId는 별도로 표시
                .mapToObj(i -> parameterNames[i] + "=" + formatValue(args[i]))
                .collect(Collectors.joining(", "));
    }

    /**
     * 값 포맷팅 (너무 긴 객체는 타입명만 표시)
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        String str = value.toString();
        if (str.length() > 100) {
            return value.getClass().getSimpleName() + "(...)";
        }
        return str;
    }
}
