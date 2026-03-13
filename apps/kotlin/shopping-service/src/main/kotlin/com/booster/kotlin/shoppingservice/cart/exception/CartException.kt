package com.booster.kotlin.shoppingservice.cart.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class CartException(errorCode: ErrorCode) : BusinessException(errorCode) {
}