package com.booster.kotlin.shoppingservice.inventory.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "inventories")
class Inventory(
    @Column(nullable = false, unique = true) val variantId: Long,
    quantity: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var quantity: Int = quantity
        private set

    @Version
    var version: Long = 0
        private set

    fun increase(amount: Int) {
        require(amount > 0) { "증가 수량은 0보다 커야 합니다" }
        this.quantity += amount
    }

    fun decrease(amount: Int) {
        require(amount > 0) { "감소 수량은 0보다 커야 합니다" }
        require(this.quantity >= amount) { "재고가 부족합니다" }
        this.quantity -= amount
    }

    companion object {
        fun create(variantId: Long, quantity: Int): Inventory =
            Inventory(variantId, quantity)
    }
    }