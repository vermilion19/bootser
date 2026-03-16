package com.booster.kotlin.shoppingservice.inventory

import com.booster.kotlin.shoppingservice.inventory.domain.Inventory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InventoryTest : DescribeSpec( {
    describe("재고 증가"){
        context("양수 수량으로 증가 시"){
            it("재고가 증가한다"){
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                inventory.increase(5)
                inventory.quantity shouldBe 15
            }
        }

        context("0이하 수량으로 증가 시"){
            it("예외가 발생한다"){
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                shouldThrow<IllegalArgumentException> { inventory.increase(0) }
            }

        }
    }

    describe("재고 차감 (decrease)") {

        context("재고보다 적은 수량 차감 시") {
            it("재고가 차감된다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                inventory.decrease(3)
                inventory.quantity shouldBe 7
            }
        }

        context("재고와 동일한 수량 차감 시") {
            it("재고가 0이 된다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                inventory.decrease(10)
                inventory.quantity shouldBe 0
            }
        }

        context("재고보다 많은 수량 차감 시") {
            it("예외가 발생한다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                shouldThrow<IllegalArgumentException> {
                    inventory.decrease(11)
                }
            }
        }

        context("0 이하 수량으로 차감 시") {
            it("예외가 발생한다") {
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                shouldThrow<IllegalArgumentException> {
                    inventory.decrease(0)
                }
            }
        }
    }

})