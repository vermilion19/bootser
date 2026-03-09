package com.booster.kotlin.boardservice.config.aop

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LogExecutionTime {
}