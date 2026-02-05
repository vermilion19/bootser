package com.booster.ddayservice.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 실행 시간을 로깅하는 어노테이션.
 *
 * <p>적용된 메서드의 실행 전/후 시간을 측정하여 로그로 출력합니다.</p>
 *
 * <pre>
 * {@code
 * @LogExecutionTime
 * public void someMethod() {
 *     // 실행 시간이 로깅됨
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {
}
