package com.booster.kotlin.shoppingservice.inventory.domain

import org.springframework.data.jpa.repository.JpaRepository

interface InventoryHistoryRepository: JpaRepository<InventoryHistory, Long> {
}