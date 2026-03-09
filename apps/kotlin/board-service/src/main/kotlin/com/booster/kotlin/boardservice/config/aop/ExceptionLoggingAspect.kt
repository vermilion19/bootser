package com.booster.kotlin.boardservice.config.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class ExceptionLoggingAspect {

    private val log = LoggerFactory.getLogger(javaClass)
    @AfterThrowing(pointcut = "execution(* com.booster.kotlin.boardservice.comment.application.*.*(..))",
        throwing = "ex")
    fun logException(joinPoint: JoinPoint, ex: Throwable){
        log.error(
            "[예외 발생] 메서드: {} | 파라미터: {} | 예외: {}",
            joinPoint.signature.toShortString(),
            joinPoint.args.toList(),
            ex.message
        )
    }
}