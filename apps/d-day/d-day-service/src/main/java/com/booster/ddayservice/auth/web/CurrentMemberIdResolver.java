package com.booster.ddayservice.auth.web;

import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentMemberIdResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMemberId.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userIdHeader = webRequest.getHeader(USER_ID_HEADER);
        CurrentMemberId annotation = parameter.getParameterAnnotation(CurrentMemberId.class);

        if (userIdHeader == null || userIdHeader.isBlank()) {
            if (annotation != null && annotation.required()) {
                throw new SpecialDayException(SpecialDayErrorCode.UNAUTHORIZED);
            }
            return null;
        }

        try {
            long memberId = Long.parseLong(userIdHeader);

            // [추가된 로직] 게이트웨이가 보낸 '게스트(-1)'인 경우 처리
            if (memberId == -1L) {
                // 필수(required=true) API인데 게스트가 들어왔다면 -> 에러 발생 (로그인 필요)
                if (annotation != null && annotation.required()) {
                    throw new SpecialDayException(SpecialDayErrorCode.UNAUTHORIZED);
                }
                // 필수 아님(required=false) -> null 반환 (비회원 모드 동작)
                return null;
            }
            return memberId;
        } catch (NumberFormatException e) {
            throw new SpecialDayException(SpecialDayErrorCode.UNAUTHORIZED);
        }
    }
}
