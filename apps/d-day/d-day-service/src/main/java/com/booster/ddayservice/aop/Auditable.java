package com.booster.ddayservice.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 감사 로그를 기록하는 어노테이션.
 *
 * <p>적용된 메서드 호출 시 다음 정보를 로깅합니다:</p>
 * <ul>
 *   <li>호출자 (memberId)</li>
 *   <li>수행한 작업 (action)</li>
 *   <li>대상 리소스 (파라미터)</li>
 *   <li>결과 (성공/실패)</li>
 * </ul>
 *
 * <pre>
 * {@code
 * @Auditable(action = "특별일 삭제")
 * public void delete(Long id, Long memberId) {
 *     // 감사 로그가 자동으로 기록됨
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 수행하는 작업 설명
     * 예: "특별일 생성", "특별일 삭제", "공개 설정 변경"
     */
    String action();
}
