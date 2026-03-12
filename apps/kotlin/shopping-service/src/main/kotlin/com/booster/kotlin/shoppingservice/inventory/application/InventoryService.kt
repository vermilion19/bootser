package com.booster.kotlin.shoppingservice.inventory.application

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.inventory.application.dto.AdjustInventoryCommand
import com.booster.kotlin.shoppingservice.inventory.domain.Inventory
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryHistory
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryHistoryRepository
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryRepository
import com.booster.kotlin.shoppingservice.inventory.exception.InventoryException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class InventoryService (
    private val inventoryRepository: InventoryRepository,
    private val inventoryHistoryRepository: InventoryHistoryRepository,
){
    @Transactional(readOnly = true)
    fun getByVariantId(variantId: Long): Inventory =
        inventoryRepository.findByVariantId(variantId) ?: throw InventoryException(ErrorCode.INVENTORY_NOT_FOUND)

    fun create(variantId: Long, quantity: Int): Inventory =
        inventoryRepository.save(Inventory.create(variantId, quantity))

    fun adjust(command: AdjustInventoryCommand): Inventory {
        val inventory = inventoryRepository.findById(command.inventoryId)
            .orElseThrow { InventoryException(ErrorCode.INVENTORY_NOT_FOUND) }

        if (command.amount > 0) inventory.increase(command.amount)
        else inventory.decrease(-command.amount)

        inventoryHistoryRepository.save(
            InventoryHistory.create(
                inventoryId = inventory.id,
                variantId = inventory.variantId,
                changeAmount = command.amount,
                remainQuantity = inventory.quantity,
                reason = command.reason,
            )
        )
        return inventory
    }

}