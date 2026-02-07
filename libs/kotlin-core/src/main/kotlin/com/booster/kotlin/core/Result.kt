package com.booster.kotlin.core

/**
 * 비즈니스 로직 결과를 표현하는 sealed class
 * 예외 대신 명시적인 성공/실패 반환
 */
sealed class Result<out T, out E> {

    data class Success<T>(val value: T) : Result<T, Nothing>()

    data class Failure<E>(val error: E) : Result<Nothing, E>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }

    companion object {
        fun <T> success(value: T): Result<T, Nothing> = Success(value)
        fun <E> failure(error: E): Result<Nothing, E> = Failure(error)

        inline fun <T> catching(block: () -> T): Result<T, Throwable> =
            try {
                Success(block())
            } catch (e: Throwable) {
                Failure(e)
            }
    }
}

// 확장 함수로 정의 (variance 문제 회피)
inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(value))
    is Result.Failure -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> = when (this) {
    is Result.Success -> transform(value)
    is Result.Failure -> this
}

inline fun <T, E> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(value)
    return this
}

inline fun <T, E> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) action(error)
    return this
}
