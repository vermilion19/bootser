package com.booster.kotlin.shoppingservice.user.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class UserException(errorCode: ErrorCode) : BusinessException(errorCode) {
}