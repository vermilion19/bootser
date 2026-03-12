package com.booster.kotlin.shoppingservice.catalog.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class CatalogException(errorCode: ErrorCode) : BusinessException(errorCode) {
}