package com.booster.kotlin.shoppingservice.shipment.web.dto.response

import com.booster.kotlin.shoppingservice.shipment.domain.Shipment
import com.booster.kotlin.shoppingservice.shipment.domain.ShipmentStatus

data class ShipmentResponse(
    val id: Long,
    val orderId: Long,
    val status: ShipmentStatus,
    val trackingNumber: String?,
    val recipientName: String,
    val recipientPhone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
) {
    companion object {
        fun from(shipment: Shipment) = ShipmentResponse(
            id = shipment.id,
            orderId = shipment.orderId,
            status = shipment.status,
            trackingNumber = shipment.trackingNumber,
            recipientName = shipment.recipientName,
            recipientPhone = shipment.recipientPhone,
            zipCode = shipment.zipCode,
            address1 = shipment.address1,
            address2 = shipment.address2,
        )
    }
}
