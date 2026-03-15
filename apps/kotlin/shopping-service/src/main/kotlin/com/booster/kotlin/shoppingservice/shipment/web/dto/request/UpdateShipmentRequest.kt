package com.booster.kotlin.shoppingservice.shipment.web.dto.request

import jakarta.validation.constraints.NotBlank

data class UpdateShipmentRequest(
    @field:NotBlank val status: String,
    val trackingNumber: String?,
    val note: String?,
)
