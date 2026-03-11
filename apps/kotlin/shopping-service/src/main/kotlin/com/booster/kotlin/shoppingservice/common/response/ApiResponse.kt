package com.booster.kotlin.shoppingservice.common.response


data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ErrorResponse?,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(true, data, null)
        fun <T> fail(error: ErrorResponse): ApiResponse<T> = ApiResponse(false,null,error)
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
)