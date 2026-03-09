package com.booster.kotlin.boardservice.config.aop

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class ExecutionTimeAspect {
    private val log = LoggerFactory.getLogger(javaClass)

    @Around("execution(* com.booster.kotlin.boardservice.comment.application.*.*(..))")
    fun measureExecutionTime(joinPoint: ProceedingJoinPoint): Any?{
        val start = System.currentTimeMillis()
        val result = joinPoint.proceed()
        val elapsed = System.currentTimeMillis() - start
        log.info("${joinPoint.signature} executed in $elapsed ms")
        return result
    }
}