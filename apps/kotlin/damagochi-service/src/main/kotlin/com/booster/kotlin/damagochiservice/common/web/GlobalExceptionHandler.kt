package com.booster.kotlin.damagochiservice.common.web

import com.booster.kotlin.damagochiservice.creature.application.ActivationCooldownException
import com.booster.kotlin.damagochiservice.creature.application.SleepToggleCooldownException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ActivationCooldownException::class)
    fun handleActivationCooldown(e: ActivationCooldownException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                code = "ACTIVATE_COOLDOWN",
                message = e.message ?: "activate cooldown",
                details = mapOf(
                    "availableAt" to e.availableAt.toString(),
                    "remainingSeconds" to e.remainingSeconds
                )
            )
        )

    @ExceptionHandler(SleepToggleCooldownException::class)
    fun handleSleepCooldown(e: SleepToggleCooldownException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                code = "SLEEP_TOGGLE_COOLDOWN",
                message = e.message ?: "sleep toggle cooldown",
                details = mapOf(
                    "availableAt" to e.availableAt.toString(),
                    "remainingSeconds" to e.remainingSeconds
                )
            )
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                code = "MISSING_HEADER",
                message = e.message ?: "missing request header"
            )
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                code = "VALIDATION_ERROR",
                message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "validation error"
            )
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "BAD_REQUEST", message = e.message ?: "bad request")
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(code = "ILLEGAL_STATE", message = e.message ?: "illegal state")
        )
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null
)




