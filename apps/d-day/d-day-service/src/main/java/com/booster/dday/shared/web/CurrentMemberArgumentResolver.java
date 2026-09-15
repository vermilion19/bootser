package com.booster.dday.shared.web;

import com.booster.core.web.exception.CoreException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code X-User-Id} 헤더를 회원 id 로 바꾼다.
 *
 * <h2>서비스가 토큰을 안 푼다</h2>
 *
 * <p>게이트웨이가 JWT 를 검증하고 이 헤더를 넣어 준다. 서비스가 다시 풀면
 * <b>검증 규칙이 두 곳에 생기고</b>, 둘이 갈라지는 날 «게이트웨이는 막았는데
 * 서비스는 통과» 또는 그 반대가 된다.
 *
 * <h2>헤더가 없으면 401 이다</h2>
 *
 * <p>개인 자원은 로그인 없이 볼 수 없다. 게이트웨이가 {@code /dday/me/**} 를
 * 게스트 통과에서 빼 두었지만(착수 0), <b>서비스가 그것에만 기대지 않는다</b> —
 * 게이트웨이를 거치지 않는 경로가 생기는 날 이 검사가 유일한 방어가 된다.
 */
@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                NativeWebRequest request, WebDataBinderFactory binderFactory) {

        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            throw new CoreException(DDayErrorCode.UNAUTHENTICATED,
                    "로그인이 필요하다 (" + HEADER + " 가 없다)");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            /* 게이트웨이가 넣는 값이라 여기 오면 우리 쪽 문제다. 그래도 400 이 아니라
               401 로 둔다 — 부르는 쪽이 고칠 수 있는 것이 「다시 로그인」뿐이다 */
            throw new CoreException(DDayErrorCode.UNAUTHENTICATED,
                    HEADER + " 가 숫자가 아니다: " + raw);
        }
    }
}
