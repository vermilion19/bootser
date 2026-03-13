package com.booster.kotlin.shoppingservice.cart.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "carts")
class Cart(
    @Column(nullable = false, unique = true) val userId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @OneToMany(mappedBy = "cart", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<CartItem> = mutableListOf()

    fun addItem(variantId: Long, quantity: Int, unitPrice: Long): CartItem {
        val existing = items.find { it.variantId == variantId && !it.isDeleted() }
        return if (existing != null) {
            existing.addQuantity(quantity)
            existing
        } else {
            val item = CartItem.create(this, variantId, quantity, unitPrice)
            items.add(item)
            item
        }
    }

    fun findActiveItems() = items.filter { !it.isDeleted() }

    fun totalPrice() = findActiveItems().sumOf { it.totalPrice() }

    companion object {
        fun create(userId: Long) = Cart(userId)
    }

}