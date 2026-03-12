package com.booster.kotlin.shoppingservice.inventory.domain

import org.springframework.data.jpa.repository.JpaRepository

interface InventoryRepository : JpaRepository<Inventory, Long> {
    fun findByVariantId(variantId: Long): Inventory?
}