package com.booster.kotlin.damagochiservice.common.web

import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserIdResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentUserId::class.java) &&
            (parameter.parameterType == Long::class.javaObjectType ||
                parameter.parameterType == java.lang.Long.TYPE)
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any {
        val value = webRequest.getHeader(USER_ID_HEADER)
            ?: throw MissingRequestHeaderException(USER_ID_HEADER, parameter)

        return value.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid $USER_ID_HEADER header: $value")
    }

    companion object {
        const val USER_ID_HEADER: String = "X-User-Id"
    }
}



