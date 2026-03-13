package com.booster.kotlin.shoppingservice.cart.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "cart_items")
class CartItem(
    @ManyToOne(fetch = FetchType.LAZY) val cart: Cart,
    @Column(nullable = false) val variantId: Long,
    quantity: Int,
    unitPrice: Long,  // 담을 때 가격 스냅샷
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var quantity: Int = quantity
        private set

    @Column(nullable = false)
    var unitPrice: Long = unitPrice
        private set

    fun addQuantity(amount: Int) {
        require(amount > 0)
        this.quantity += amount
    }

    fun updateQuantity(amount: Int) {
        require(amount > 0) { "수량은 1 이상이어야 합니다" }
        this.quantity = amount
    }

    fun totalPrice() = unitPrice * quantity

    companion object {
        fun create(cart: Cart, variantId: Long, quantity: Int, unitPrice: Long) =
            CartItem(cart, variantId, quantity, unitPrice)
    }

}

