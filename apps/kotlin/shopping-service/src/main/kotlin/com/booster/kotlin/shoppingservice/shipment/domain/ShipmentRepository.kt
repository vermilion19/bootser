package com.booster.kotlin.shoppingservice.shipment.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ShipmentRepository : JpaRepository<Shipment, Long> {
    fun findByOrderId(orderId: Long): Optional<Shipment>
}
