package com.booster.kotlin.boardservice.config.aop

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class RetryAspect {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(Retry)")
    fun retry(joinPoint: ProceedingJoinPoint): Any?{
        val retry = (joinPoint.signature as MethodSignature)
            .method
            .getAnnotation(Retry::class.java)

        var lastException: Exception? = null
        repeat(retry.maxAttempts) { attempt ->
            try {
                return joinPoint.proceed()
            }catch (ex: Exception) {
                lastException = ex
                log.warn(
                    "[재시도] {}/{} | 메서드: {} | 예외: {}",
                    attempt + 1, retry.maxAttempts,
                    joinPoint.signature.name,
                    ex.message
                )
                if (attempt < retry.maxAttempts - 1) {
                    Thread.sleep(retry.delayMs)
                }
            }
        }
        throw lastException!!
    }

}