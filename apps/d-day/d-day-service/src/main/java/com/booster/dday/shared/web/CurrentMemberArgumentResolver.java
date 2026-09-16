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
 *
 * <h2>⚠ 게스트에게도 헤더가 붙어 온다 — {@code -1} 이다</h2>
 *
 * <p>게이트웨이는 토큰 없는 d-day 요청을 <b>게스트로 통과시키면서
 * {@code X-User-Id: -1} 을 넣는다</b> ({@code handleGuestAccess}). 그러므로
 * «헤더가 있다» 가 «로그인했다» 를 뜻하지 않는다.
 *
 * <p>{@code /me/**} 는 게스트 통과에서 빠져 있어 지금까지 이 값을 만날 일이
 * 없었지만, <b>통합 검색은 게스트도 부르는 주소</b>라 실제로 온다. 그대로 두면
 * {@code -1} 이 회원 번호가 되어 «회원 -1 의 기념일» 을 찾게 되고, 언젠가 누가
 * 그 번호로 행을 만드는 날 <b>게스트 전원이 같은 개인 자료를 본다.</b>
 *
 * <p>그래서 {@code -1} 이하를 <b>로그인 안 한 것</b>으로 본다. 게이트웨이가 막아
 * 주는 것에 기대지 않는 것과 같은 판단이다.
 */
@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "X-User-Id";

    /** 게이트웨이가 게스트에게 넣는 값 ({@code handleGuestAccess}) */
    static final long GUEST = -1L;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                NativeWebRequest request, WebDataBinderFactory binderFactory) {

        boolean required = parameter.getParameterAnnotation(CurrentMember.class).required();
        Long memberId = memberIdOf(request.getHeader(HEADER));

        if (memberId == null && required) {
            throw new CoreException(DDayErrorCode.UNAUTHENTICATED,
                    "로그인이 필요하다 (" + HEADER + " 가 없거나 게스트다)");
        }
        return memberId;
    }

    /**
     * 헤더를 회원 번호로 읽는다. <b>로그인 안 한 것은 {@code null}</b> 이다.
     *
     * <p>세 가지가 같은 뜻이 된다 — 헤더가 없거나, 게스트({@code -1})거나, 숫자가
     * 아니거나. 부르는 쪽이 할 수 있는 일이 「로그인」 하나뿐이라 가르지 않는다.
     */
    private static Long memberIdOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed <= GUEST ? null : parsed;
        } catch (NumberFormatException e) {
            /* 게이트웨이가 넣는 값이라 여기 오면 우리 쪽 문제다. 그래도 400 이 아니라
               로그인 안 한 것으로 본다 — 부르는 쪽이 고칠 수 있는 것이 그것뿐이다 */
            return null;
        }
    }
}
