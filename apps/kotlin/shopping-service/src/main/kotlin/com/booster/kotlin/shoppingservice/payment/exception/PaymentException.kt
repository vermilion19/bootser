package com.booster.kotlin.shoppingservice.payment.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class PaymentException(errorCode: ErrorCode) : BusinessException(errorCode)