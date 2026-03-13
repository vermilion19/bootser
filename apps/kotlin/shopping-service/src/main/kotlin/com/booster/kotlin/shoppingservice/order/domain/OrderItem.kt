package com.booster.kotlin.shoppingservice.order.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "order_items")
class OrderItem(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false) val order: Order,
    @Column(nullable = false) val variantId: Long,
    @Column(nullable = false) val productName: String,   // 스냅샷
    @Column(nullable = false) val variantSku: String,    // 스냅샷
    @Column(nullable = false) val unitPrice: Long,       // 스냅샷
    @Column(nullable = false) val quantity: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val totalPrice: Long = unitPrice * quantity

    companion object {
        fun create(
            order: Order,
            variantId: Long,
            productName: String,
            variantSku: String,
            unitPrice: Long,
            quantity: Int,
        ) = OrderItem(order, variantId, productName, variantSku, unitPrice, quantity)
    }
}
