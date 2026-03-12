package com.booster.kotlin.shoppingservice.inventory.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "inventory_histories")
class InventoryHistory(
    @Column(nullable = false) val inventoryId: Long,
    @Column(nullable = false) val variantId: Long,
    @Column(nullable = false) val changeAmount: Int,
    @Column(nullable = false) val remainQuantity: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val reason: ChangeReason,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    enum class ChangeReason {
        RESTOCK, ORDER, CANCEL, ADJUSTMENT
    }

    companion object {
        fun create(
            inventoryId: Long,
            variantId: Long,
            changeAmount: Int,
            remainQuantity: Int,
            reason: ChangeReason,
        ): InventoryHistory = InventoryHistory(inventoryId, variantId, changeAmount, remainQuantity, reason)
    }
}
