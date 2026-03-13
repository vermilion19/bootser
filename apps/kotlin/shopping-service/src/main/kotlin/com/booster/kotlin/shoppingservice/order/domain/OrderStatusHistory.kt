package com.booster.kotlin.shoppingservice.order.domain

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
@Table(name = "order_status_histories")
class OrderStatusHistory(
    @Column(nullable = false) val orderId: Long,
    @Enumerated(EnumType.STRING) @Column val fromStatus: OrderStatus?,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val toStatus: OrderStatus,
    @Column val reason: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    companion object {
        fun create(orderId: Long, fromStatus: OrderStatus?, toStatus: OrderStatus, reason: String? = null) =
            OrderStatusHistory(orderId, fromStatus, toStatus, reason)
    }
}
