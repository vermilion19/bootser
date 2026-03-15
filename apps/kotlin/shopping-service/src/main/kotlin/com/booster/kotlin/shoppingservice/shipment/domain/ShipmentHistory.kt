package com.booster.kotlin.shoppingservice.shipment.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "shipment_histories")
class ShipmentHistory(
    @Column(nullable = false) val shipmentId: Long,
    @Enumerated(EnumType.STRING) @Column val fromStatus: ShipmentStatus?,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val toStatus: ShipmentStatus,
    @Column val note: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    companion object {
        fun create(
            shipmentId: Long,
            fromStatus: ShipmentStatus?,
            toStatus: ShipmentStatus,
            note: String? = null,
        ) = ShipmentHistory(shipmentId, fromStatus, toStatus, note)
    }
}
