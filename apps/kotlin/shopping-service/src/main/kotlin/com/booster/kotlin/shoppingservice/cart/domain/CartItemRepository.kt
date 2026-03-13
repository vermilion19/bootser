package com.booster.kotlin.shoppingservice.cart.domain

import org.springframework.data.jpa.repository.JpaRepository

interface CartItemRepository : JpaRepository<CartItem, Long> {
}