package com.booster.kotlin.shoppingservice.common.exception

open class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.message
) : RuntimeException(message)