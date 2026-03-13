package com.booster.kotlin.shoppingservice.order.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class OrderException(errorCode: ErrorCode) : BusinessException(errorCode) {
}