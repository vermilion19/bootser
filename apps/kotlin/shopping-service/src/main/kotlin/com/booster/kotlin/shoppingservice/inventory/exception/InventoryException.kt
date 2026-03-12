package com.booster.kotlin.shoppingservice.inventory.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class InventoryException(errorCode: ErrorCode) : BusinessException(errorCode) {
}