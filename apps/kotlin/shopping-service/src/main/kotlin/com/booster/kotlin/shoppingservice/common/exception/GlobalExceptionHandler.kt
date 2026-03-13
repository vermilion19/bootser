package com.booster.kotlin.shoppingservice.common.exception

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.common.response.ErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        val error = ErrorResponse(
            code = e.errorCode.name,
            message = e.message ?: e.errorCode.message
        )
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.fail(error))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", "){ "${it.field}: ${it.defaultMessage}"}
        val error = ErrorResponse(
            code = ErrorCode.INVALID_INPUT.name,
            message = message
        )
        return ResponseEntity
            .status(400)
            .body(ApiResponse.fail(error))
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: ObjectOptimisticLockingFailureException): ResponseEntity<ApiResponse<Nothing>> {
        val error = ErrorResponse(
            code = ErrorCode.INSUFFICIENT_STOCK.name,
            message = ErrorCode.INSUFFICIENT_STOCK.message,
        )
        return ResponseEntity.status(409).body(ApiResponse.fail(error))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        val error = ErrorResponse(
            code = ErrorCode.INTERNAL_ERROR.name,
            message = ErrorCode.INTERNAL_ERROR.message
        )
        return ResponseEntity
            .status(500)
            .body(ApiResponse.fail(error))
    }


}