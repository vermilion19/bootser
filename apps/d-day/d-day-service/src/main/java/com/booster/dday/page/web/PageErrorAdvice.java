package com.booster.dday.page.web;

import com.booster.core.web.exception.CoreException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * 화면에서 터진 것을 <b>HTML 로</b> 돌려준다.
 *
 * <h2>왜 따로 있나</h2>
 *
 * <p>{@code libs} 의 {@code GlobalExceptionHandler} 는 {@code @RestControllerAdvice}
 * 라 <b>JSON 을 낸다.</b> 그것이 화면에 걸리면 브라우저에
 * {@code {"result":"ERROR",...}} 가 그대로 찍힌다 — 사람이 볼 화면에서는 그것이
 * 곧 고장이다.
 *
 * <p>{@code basePackages} 로 <b>이 패키지의 컨트롤러에만</b> 건다. 그래야 API 쪽은
 * 지금까지처럼 JSON 을 낸다 — 한 서비스가 두 가지를 내는 이상 처리기도 둘이어야
 * 한다.
 *
 * <p>{@link Order} 가 있는 것은 {@code @ExceptionHandler(Exception.class)} 를 양쪽이
 * 들고 있기 때문이다. 순서를 안 주면 <b>어느 쪽이 잡을지 정해지지 않는다.</b>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(basePackages = "com.booster.dday.page.web")
public class PageErrorAdvice {

    static final String VIEW = "page/error";

    /**
     * 로그인이 필요한 화면인데 로그인을 안 했다.
     *
     * <p>API 라면 401 로 끝이지만 <b>화면에서는 빈 401 이 흰 화면</b>이다.
     * 로그인으로 보낸다 — 사람이 할 수 있는 일이 그것뿐이다.
     */
    @ExceptionHandler(CoreException.class)
    public ModelAndView handleCoreException(CoreException e) {
        HttpStatus status = HttpStatus.valueOf(e.getErrorCode().getStatus());
        if (status == HttpStatus.UNAUTHORIZED) {
            return new ModelAndView("redirect:/dday/login");
        }

        log.warn("[Page] {} — {}", e.getErrorCode().getCode(), e.getMessage());
        return page(status, e.getErrorCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e) {
        log.error("[Page] 화면을 그리다 터졌다", e);

        /* 속을 사용자에게 보이지 않는다. 무엇이 터졌는지는 로그에 있다 */
        return page(HttpStatus.INTERNAL_SERVER_ERROR, null, "잠시 뒤에 다시 시도해 주세요.");
    }

    /**
     * 오류 화면.
     *
     * <p>{@link ModelAndView} 로 돌려주는 까닭은 <b>상태 코드 때문</b>이다. 뷰 이름만
     * 돌려주면 본문은 오류인데 응답은 200 이 나간다 — 브라우저에는 그럴듯해 보이지만
     * <b>크롤러도 모니터링도 「잘 됐다」로 읽는다.</b> 띄워 보고 찾았다.
     */
    private static ModelAndView page(HttpStatus status, String code, String message) {
        ModelAndView view = new ModelAndView(VIEW, status);
        view.addObject("status", status.value());
        view.addObject("code", code);
        view.addObject("message", message);
        return view;
    }
}
