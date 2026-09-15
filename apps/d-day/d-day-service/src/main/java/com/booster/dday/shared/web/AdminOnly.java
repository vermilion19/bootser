package com.booster.dday.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 운영자만 부를 수 있다 (ARCHITECTURE §7.3).
 *
 * <p>게이트웨이는 {@code /api/v1/dday/admin/**} 를 <b>토큰을 보기도 전에 403</b>
 * 으로 막는다 — admin 은 게이트웨이를 통과하지 않고 내부 접근 전용이다. 그러면
 * 이 검사는 무엇을 막나.
 *
 * <p><b>게이트웨이를 거치지 않는 경로가 생겼다는 것 자체가 이 검사의 이유다.</b>
 * 내부망에서 직접 부를 수 있게 된 순간 게이트웨이는 그 요청을 못 본다 — 서비스가
 * 유일한 방어가 아니라 <b>마지막 방어</b>여야 한다.
 *
 * <p>같은 판단을 {@link CurrentMember} 에서도 했다. 게이트웨이가 막아 주기로 돼
 * 있는 것에만 기대지 않는다.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {
}
