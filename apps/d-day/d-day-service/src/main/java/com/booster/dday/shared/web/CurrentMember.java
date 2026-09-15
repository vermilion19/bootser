package com.booster.dday.shared.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 지금 요청을 보낸 회원의 id.
 *
 * <p>게이트웨이가 JWT 를 풀어 {@code X-User-Id} 헤더로 넣어 준다. 서비스는 토큰을
 * 직접 다루지 않는다 — <b>인증은 정문에서 한 번만</b> 한다.
 *
 * <p>이 애노테이션이 붙은 인자는 <b>언제나 채워져 있다.</b> 헤더가 없으면 컨트롤러에
 * 닿기 전에 401 이다 ({@code CurrentMemberArgumentResolver}). 그래서 컨트롤러가
 * «없으면 어쩌지» 를 묻지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {
}
