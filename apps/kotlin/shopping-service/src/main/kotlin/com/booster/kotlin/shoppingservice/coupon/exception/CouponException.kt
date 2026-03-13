package com.booster.kotlin.shoppingservice.coupon.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class CouponException(errorCode: ErrorCode) : BusinessException(errorCode)