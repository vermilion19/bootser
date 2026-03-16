package com.booster.kotlin.shoppingservice.cart

import com.booster.kotlin.shoppingservice.cart.domain.Cart
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize

class CartTest : DescribeSpec({

    describe("장바구니 아이템 추가 (addItem)") {

        context("새로운 상품을 담을 때") {
            it("아이템이 추가된다") {
                val cart = Cart.create(userId = 1L)

                cart.addItem(variantId = 10L, quantity = 2, unitPrice = 5000L)

                cart.findActiveItems() shouldHaveSize 1
            }

            it("추가된 아이템의 수량과 단가가 정확히 저장된다") {
                val cart = Cart.create(userId = 1L)

                val item = cart.addItem(variantId = 10L, quantity = 3, unitPrice = 1000L)

                item.quantity shouldBe 3
                item.unitPrice shouldBe 1000L
            }
        }

        context("이미 담긴 상품을 다시 담을 때") {
            it("새 아이템을 추가하지 않고 기존 수량에 합산한다") {
                val cart = Cart.create(userId = 1L)
                cart.addItem(variantId = 10L, quantity = 2, unitPrice = 5000L)

                cart.addItem(variantId = 10L, quantity = 3, unitPrice = 5000L)

                cart.findActiveItems() shouldHaveSize 1
                cart.findActiveItems()[0].quantity shouldBe 5
            }
        }

        context("서로 다른 상품을 각각 담을 때") {
            it("아이템이 각각 별도로 추가된다") {
                val cart = Cart.create(userId = 1L)

                cart.addItem(variantId = 10L, quantity = 1, unitPrice = 1000L)
                cart.addItem(variantId = 20L, quantity = 1, unitPrice = 2000L)

                cart.findActiveItems() shouldHaveSize 2
            }
        }
    }

    describe("활성 아이템 조회 (findActiveItems)") {

        context("소프트 삭제된 아이템이 있을 때") {
            it("삭제된 아이템은 제외하고 반환한다") {
                val cart = Cart.create(userId = 1L)
                cart.addItem(variantId = 10L, quantity = 1, unitPrice = 1000L)
                val item2 = cart.addItem(variantId = 20L, quantity = 1, unitPrice = 2000L)
                item2.softDelete()

                val activeItems = cart.findActiveItems()

                activeItems shouldHaveSize 1
                activeItems[0].variantId shouldBe 10L
            }
        }

        context("모든 아이템이 삭제된 경우") {
            it("빈 리스트를 반환한다") {
                val cart = Cart.create(userId = 1L)
                val item = cart.addItem(variantId = 10L, quantity = 1, unitPrice = 1000L)
                item.softDelete()

                cart.findActiveItems() shouldHaveSize 0
            }
        }
    }

    describe("총 금액 계산 (totalPrice)") {

        context("여러 아이템이 담긴 경우") {
            it("활성 아이템의 금액 합산이 반환된다") {
                val cart = Cart.create(userId = 1L)
                cart.addItem(variantId = 10L, quantity = 2, unitPrice = 1000L) // 2000
                cart.addItem(variantId = 20L, quantity = 3, unitPrice = 500L)  // 1500

                cart.totalPrice() shouldBe 3500L
            }
        }

        context("삭제된 아이템이 포함된 경우") {
            it("삭제된 아이템 금액은 합산에서 제외된다") {
                val cart = Cart.create(userId = 1L)
                cart.addItem(variantId = 10L, quantity = 2, unitPrice = 1000L) // 2000
                val deletedItem = cart.addItem(variantId = 20L, quantity = 1, unitPrice = 9999L)
                deletedItem.softDelete()

                cart.totalPrice() shouldBe 2000L
            }
        }

        context("장바구니가 비어있는 경우") {
            it("0을 반환한다") {
                val cart = Cart.create(userId = 1L)

                cart.totalPrice() shouldBe 0L
            }
        }
    }
})