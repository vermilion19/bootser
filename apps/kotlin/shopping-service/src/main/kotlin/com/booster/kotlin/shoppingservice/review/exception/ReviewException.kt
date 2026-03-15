package com.booster.kotlin.shoppingservice.review.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class ReviewException(errorCode: ErrorCode) : BusinessException(errorCode)
