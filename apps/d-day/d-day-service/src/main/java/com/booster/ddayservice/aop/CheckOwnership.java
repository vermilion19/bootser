package com.booster.ddayservice.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 리소스 소유권을 검증하는 어노테이션.
 *
 * <p>적용된 메서드 실행 전에 해당 리소스가 요청자의 소유인지 확인합니다.</p>
 *
 * <h3>동작 방식:</h3>
 * <ol>
 *   <li>파라미터에서 id와 memberId 추출</li>
 *   <li>id로 엔티티 조회</li>
 *   <li>엔티티의 소유자가 memberId와 일치하는지 확인</li>
 *   <li>불일치 시 FORBIDDEN 예외 발생</li>
 * </ol>
 *
 * <pre>
 * {@code
 * @CheckOwnership
 * public void delete(Long id, Long memberId) {
 *     // 소유권 검증이 이미 완료된 상태
 *     specialDayRepository.deleteById(id);
 * }
 * }
 * </pre>
 *
 * <h3>파라미터 규칙:</h3>
 * <ul>
 *   <li>첫 번째 파라미터: 리소스 ID (Long)</li>
 *   <li>"memberId"라는 이름의 파라미터: 요청자 ID (Long)</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckOwnership {
}
