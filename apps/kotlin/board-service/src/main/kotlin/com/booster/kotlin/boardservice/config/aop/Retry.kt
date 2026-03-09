package com.booster.kotlin.boardservice.config.aop

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Retry(
    val maxAttempts: Int = 3,
    val delayMs: Long = 1000L,
)
