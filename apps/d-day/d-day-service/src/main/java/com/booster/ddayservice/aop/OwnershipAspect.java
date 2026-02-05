package com.booster.ddayservice.aop;

import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * {@link CheckOwnership} 어노테이션이 적용된 메서드의 리소스 소유권을 검증하는 Aspect.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final SpecialDayRepository specialDayRepository;

    /**
     * @Before: 메서드 실행 전에 소유권 검증
     * - 검증 실패 시 예외를 던져서 원본 메서드 실행을 막음
     */
    @Before("@annotation(CheckOwnership)")
    public void checkOwnership(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // 파라미터 추출
        Long id = extractId(parameterNames, args);
        Long memberId = extractMemberId(parameterNames, args);

        if (id == null) {
            log.warn("[OWNERSHIP] id 파라미터를 찾을 수 없음: method={}",
                    signature.getMethod().getName());
            return;  // id가 없으면 검증 스킵 (신규 생성 등)
        }

        if (memberId == null) {
            log.warn("[OWNERSHIP] memberId 파라미터를 찾을 수 없음: method={}",
                    signature.getMethod().getName());
            throw new SpecialDayException(SpecialDayErrorCode.FORBIDDEN);
        }

        // 엔티티 조회 및 소유권 검증
        SpecialDay specialDay = specialDayRepository.findById(id)
                .orElseThrow(() -> new SpecialDayException(SpecialDayErrorCode.SPECIAL_DAY_NOT_FOUND));

        if (!specialDay.isOwnedBy(memberId)) {
            log.warn("[OWNERSHIP] 소유권 검증 실패: id={}, memberId={}, ownerId={}",
                    id, memberId, specialDay.getMemberId());
            throw new SpecialDayException(SpecialDayErrorCode.FORBIDDEN);
        }

        log.debug("[OWNERSHIP] 소유권 검증 통과: id={}, memberId={}", id, memberId);
    }

    /**
     * 첫 번째 Long 타입 파라미터 또는 "id" 이름의 파라미터를 id로 추출
     */
    private Long extractId(String[] parameterNames, Object[] args) {
        // 1. "id" 이름의 파라미터 먼저 찾기
        for (int i = 0; i < parameterNames.length; i++) {
            if ("id".equals(parameterNames[i]) && args[i] instanceof Long) {
                return (Long) args[i];
            }
        }

        // 2. 첫 번째 Long 타입 파라미터 사용
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }

        return null;
    }

    /**
     * "memberId" 이름의 파라미터 추출
     */
    private Long extractMemberId(String[] parameterNames, Object[] args) {
        for (int i = 0; i < parameterNames.length; i++) {
            if ("memberId".equals(parameterNames[i]) && args[i] instanceof Long) {
                return (Long) args[i];
            }
        }
        return null;
    }
}
