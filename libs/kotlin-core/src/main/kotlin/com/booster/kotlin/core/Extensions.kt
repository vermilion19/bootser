package com.booster.kotlin.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kotlin 확장 함수 및 유틸리티
 */

// Logger 확장
inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

// Null-safe 확장
inline fun <T, R> T?.ifNotNull(block: (T) -> R): R? = this?.let(block)

inline fun <T> T?.orThrow(lazyMessage: () -> String): T =
    this ?: throw IllegalArgumentException(lazyMessage())

// Collection 확장
fun <T> Collection<T>.ifNotEmpty(block: (Collection<T>) -> Unit) {
    if (this.isNotEmpty()) block(this)
}

// String 확장
fun String.toSnakeCase(): String =
    this.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

fun String.toCamelCase(): String =
    this.split("_").mapIndexed { index, s ->
        if (index == 0) s.lowercase()
        else s.lowercase().replaceFirstChar { it.uppercase() }
    }.joinToString("")
