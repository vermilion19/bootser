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
@Table(name = "shipments")
class Shipment(
    @Column(nullable = false) val orderId: Long,
    @Column(nullable = false) val recipientName: String,
    @Column(nullable = false) val recipientPhone: String,
    @Column(nullable = false) val zipCode: String,
    @Column(nullable = false) val address1: String,
    @Column val address2: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ShipmentStatus = ShipmentStatus.READY
        private set

    @Column
    var trackingNumber: String? = null
        private set

    fun ship(trackingNumber: String) {
        check(status.canShip()) { "배송 시작 처리할 수 없는 상태입니다" }
        this.trackingNumber = trackingNumber
        status = ShipmentStatus.SHIPPED
    }

    fun deliver() {
        check(status.canDeliver()) { "배송 완료 처리할 수 없는 상태입니다" }
        status = ShipmentStatus.DELIVERED
    }

    companion object {
        fun create(
            orderId: Long,
            recipientName: String,
            recipientPhone: String,
            zipCode: String,
            address1: String,
            address2: String?,
        ) = Shipment(orderId, recipientName, recipientPhone, zipCode, address1, address2)
    }
}
