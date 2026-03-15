package com.booster.kotlin.shoppingservice.shipment.exception

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode

class ShipmentException(errorCode: ErrorCode) : BusinessException(errorCode)
