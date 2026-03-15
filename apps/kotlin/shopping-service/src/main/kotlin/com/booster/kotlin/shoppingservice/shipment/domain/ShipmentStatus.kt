package com.booster.kotlin.shoppingservice.shipment.domain

enum class ShipmentStatus {
    READY,
    SHIPPED,
    DELIVERED;

    fun canShip() = this == READY
    fun canDeliver() = this == SHIPPED
}
