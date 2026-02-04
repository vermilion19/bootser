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
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            throw new SpecialDayException(SpecialDayErrorCode.UNAUTHORIZED);
        }
    }
}
