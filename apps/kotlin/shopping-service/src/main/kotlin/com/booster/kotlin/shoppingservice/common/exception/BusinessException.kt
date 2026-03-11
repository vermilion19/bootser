package com.booster.kotlin.shoppingservice.common.exception

class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.message
) : RuntimeException(message)