package com.booster.ddayservice.auth.web;

import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentMemberIdResolver implements HandlerMethodArgumentResolver {

    public static final String MEMBER_ID_ATTRIBUTE = "currentMemberId";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMemberId.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object memberId = webRequest.getAttribute(MEMBER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        CurrentMemberId annotation = parameter.getParameterAnnotation(CurrentMemberId.class);

        if (memberId == null && annotation != null && annotation.required()) {
            throw new SpecialDayException(SpecialDayErrorCode.UNAUTHORIZED);
        }

        return memberId;
    }
}
