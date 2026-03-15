package com.booster.kotlin.shoppingservice.shipment.application.dto

data class UpdateShipmentStatusCommand(
    val shipmentId: Long,
    val status: String,
    val trackingNumber: String?,
    val note: String?,
)
