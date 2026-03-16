package com.booster.kotlin.shoppingservice.inventory

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.inventory.application.InventoryService
import com.booster.kotlin.shoppingservice.inventory.application.dto.AdjustInventoryCommand
import com.booster.kotlin.shoppingservice.inventory.domain.Inventory
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryHistory
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryHistoryRepository
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryRepository
import com.booster.kotlin.shoppingservice.inventory.exception.InventoryException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class InventoryServiceTest : DescribeSpec({

    val inventoryRepository = mockk<InventoryRepository>()
    val inventoryHistoryRepository = mockk<InventoryHistoryRepository>()
    val service = InventoryService(inventoryRepository, inventoryHistoryRepository)

    describe("getByVariantId") {

        context("재고가 존재하면") {
            it("해당 Inventory를 반환한다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                every { inventoryRepository.findByVariantId(1L) } returns inventory

                val result = service.getByVariantId(1L)

                result shouldBe inventory
            }
        }

        context("재고가 없으면") {
            it("INVENTORY_NOT_FOUND 예외를 던진다") {
                every { inventoryRepository.findByVariantId(99L) } returns null

                val ex = shouldThrow<InventoryException> { service.getByVariantId(99L) }
                ex.errorCode shouldBe ErrorCode.INVENTORY_NOT_FOUND
            }
        }
    }

    describe("create") {

        context("variantId와 초기 수량을 전달하면") {
            it("Inventory를 저장하고 반환한다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 50)
                every { inventoryRepository.save(any()) } returns inventory

                val result = service.create(variantId = 1L, quantity = 50)

                result.quantity shouldBe 50
                verify(exactly = 1) { inventoryRepository.save(any()) }
            }
        }
    }

    describe("adjust") {

        context("amount가 양수이면") {
            it("재고를 증가시키고 이력을 저장한다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                val command = AdjustInventoryCommand(
                    inventoryId = 1L,
                    amount = 5,
                    reason = InventoryHistory.ChangeReason.RESTOCK,
                )
                val historySlot = slot<InventoryHistory>()

                every { inventoryRepository.findById(1L) } returns Optional.of(inventory)
                every { inventoryHistoryRepository.save(capture(historySlot)) } answers { firstArg() }

                val result = service.adjust(command)

                result.quantity shouldBe 15
                historySlot.captured.changeAmount shouldBe 5
                historySlot.captured.remainQuantity shouldBe 15
                historySlot.captured.reason shouldBe InventoryHistory.ChangeReason.RESTOCK
            }
        }

        context("amount가 음수이면") {
            it("재고를 차감하고 이력을 저장한다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                val command = AdjustInventoryCommand(
                    inventoryId = 1L,
                    amount = -3,
                    reason = InventoryHistory.ChangeReason.ORDER,
                )
                val historySlot = slot<InventoryHistory>()

                every { inventoryRepository.findById(1L) } returns Optional.of(inventory)
                every { inventoryHistoryRepository.save(capture(historySlot)) } answers { firstArg() }

                val result = service.adjust(command)

                result.quantity shouldBe 7
                historySlot.captured.changeAmount shouldBe -3
                historySlot.captured.remainQuantity shouldBe 7
            }
        }

        context("재고가 존재하지 않으면") {
            it("INVENTORY_NOT_FOUND 예외를 던진다") {
                every { inventoryRepository.findById(99L) } returns Optional.empty()

                val command = AdjustInventoryCommand(99L, 5, InventoryHistory.ChangeReason.RESTOCK)

                val ex = shouldThrow<InventoryException> { service.adjust(command) }
                ex.errorCode shouldBe ErrorCode.INVENTORY_NOT_FOUND
            }
        }
    }
})