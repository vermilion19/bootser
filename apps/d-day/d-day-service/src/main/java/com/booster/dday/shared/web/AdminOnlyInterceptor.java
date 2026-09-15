package com.booster.dday.shared.web;

import com.booster.core.web.exception.CoreException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link AdminOnly} 가 붙은 핸들러를 {@code X-User-Role} 로 가른다.
 *
 * <h2>왜 인자 해석기가 아니라 인터셉터인가</h2>
 *
 * <p>{@code @CurrentMember} 는 <b>값을 만드는</b> 일이라 인자 자리에 붙는다. 이것은
 * <b>부를 수 있는지를 정하는</b> 일이고, 인자로 두면 <b>그 인자를 안 적은 메서드가
 * 검사 없이 열린다.</b> 검사를 잊을 수 있는 자리에 두지 않는다.
 *
 * <h2>없는 헤더는 401, 틀린 역할은 403</h2>
 *
 * <p>가르는 까닭은 부르는 쪽이 할 일이 다르기 때문이다 — 앞은 「누구인지 밝혀라」고
 * 뒤는 「당신은 안 된다」다. 둘을 한 코드로 묶으면 운영자가 <b>헤더를 빠뜨린 것인지
 * 권한이 없는 것인지</b> 응답만 보고 알 수 없다.
 *
 * <h2>{@code require-role} 을 끌 수 있다</h2>
 *
 * <p>이 주소는 게이트웨이를 안 타므로 <b>헤더를 넣어 줄 사람이 없다.</b> 운영자가
 * 직접 부를 때는 {@code -H "X-User-Role: ROLE_ADMIN"} 을 붙이면 되지만, DB 도
 * Redis 도 없이 띄워 보는 프로필에서는 그것조차 번거롭다. <b>기본값은 켜짐이다</b> —
 * 끄는 쪽이 설정을 적어야 하고, 그래서 설정 파일을 읽으면 꺼져 있는 것이 보인다.
 */
@Slf4j
@Component
public class AdminOnlyInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-User-Role";
    static final String ADMIN = "ROLE_ADMIN";

    private final boolean requireRole;

    public AdminOnlyInterceptor(
            @Value("${dday.admin.require-role:true}") boolean requireRole) {
        this.requireRole = requireRole;
        if (!requireRole) {
            log.warn("[Admin] X-User-Role 검사가 꺼져 있다 — 운영 프로필에서는 켜 둘 것");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {

        if (!(handler instanceof HandlerMethod method) || !isAdminOnly(method)) {
            return true;
        }
        if (!requireRole) {
            return true;
        }

        String role = request.getHeader(HEADER);
        if (role == null || role.isBlank()) {
            throw new CoreException(DDayErrorCode.UNAUTHENTICATED,
                    "운영자 주소다 (" + HEADER + " 가 없다)");
        }
        if (!ADMIN.equalsIgnoreCase(role.trim())) {
            log.warn("[Admin] {} 가 {} 를 불렀다 — 막는다", role, request.getRequestURI());
            throw new CoreException(DDayErrorCode.FORBIDDEN,
                    "운영자만 부를 수 있다: " + request.getRequestURI());
        }
        return true;
    }

    /** 메서드에 붙었거나 그 컨트롤러 전체에 붙었으면 검사한다 */
    private static boolean isAdminOnly(HandlerMethod method) {
        return method.hasMethodAnnotation(AdminOnly.class)
                || method.getBeanType().isAnnotationPresent(AdminOnly.class);
    }
}
