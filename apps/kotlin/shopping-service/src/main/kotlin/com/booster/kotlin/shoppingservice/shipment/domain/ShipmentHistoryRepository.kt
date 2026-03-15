package com.booster.kotlin.shoppingservice.shipment.domain

import org.springframework.data.jpa.repository.JpaRepository

interface ShipmentHistoryRepository : JpaRepository<ShipmentHistory, Long>
