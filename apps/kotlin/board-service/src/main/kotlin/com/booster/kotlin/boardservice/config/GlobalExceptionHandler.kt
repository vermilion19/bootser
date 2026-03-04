package com.booster.kotlin.boardservice.config

import com.booster.kotlin.boardservice.post.exception.PostNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(PostNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlePostNotFound(e: PostNotFoundException): Map<String, Any> {
        return mapOf(
            "status" to 404,
            "message" to (e.message ?: "Post not found")
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(e: IllegalArgumentException): Map<String, Any> {
        return mapOf(
            "status" to 400,
            "message" to (e.message ?: "Invalid request")
        )
    }
}