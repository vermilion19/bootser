package com.booster.kotlin.shoppingservice.cart

import com.booster.kotlin.shoppingservice.cart.application.CartService
import com.booster.kotlin.shoppingservice.cart.application.dto.AddCartItemCommand
import com.booster.kotlin.shoppingservice.cart.application.dto.UpdateCartItemCommand
import com.booster.kotlin.shoppingservice.cart.domain.Cart
import com.booster.kotlin.shoppingservice.cart.domain.CartItem
import com.booster.kotlin.shoppingservice.cart.domain.CartItemRepository
import com.booster.kotlin.shoppingservice.cart.domain.CartRepository
import com.booster.kotlin.shoppingservice.cart.exception.CartException
import com.booster.kotlin.shoppingservice.catalog.domain.ProductVariant
import com.booster.kotlin.shoppingservice.catalog.domain.ProductVariantRepository
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.inventory.domain.Inventory
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class CartServiceTest : DescribeSpec({

    val cartRepository = mockk<CartRepository>()
    val cartItemRepository = mockk<CartItemRepository>()
    val variantRepository = mockk<ProductVariantRepository>()
    val inventoryRepository = mockk<InventoryRepository>()
    val service = CartService(cartRepository, cartItemRepository, variantRepository, inventoryRepository)

    describe("getCart") {

        context("장바구니가 존재하면") {
            it("저장된 장바구니를 반환한다") {
                val cart = Cart.create(userId = 1L)
                every { cartRepository.findByUserId(1L) } returns cart

                val result = service.getCart(1L)

                result shouldBe cart
            }
        }

        context("장바구니가 없으면") {
            it("빈 Cart 객체를 반환한다 (DB 저장 없음)") {
                every { cartRepository.findByUserId(1L) } returns null

                val result = service.getCart(1L)

                result.userId shouldBe 1L
                verify(exactly = 0) { cartRepository.save(any()) }
            }
        }
    }

    describe("addItem") {

        context("Variant가 존재하지 않으면") {
            it("PRODUCT_VARIANT_NOT_FOUND 예외를 던진다") {
                every { variantRepository.findById(any()) } returns Optional.empty()

                val command = AddCartItemCommand(userId = 1L, variantId = 99L, quantity = 1)

                val ex = shouldThrow<CartException> { service.addItem(command) }
                ex.errorCode shouldBe ErrorCode.PRODUCT_VARIANT_NOT_FOUND
            }
        }

        context("재고 정보가 없으면") {
            it("INVENTORY_NOT_FOUND 예외를 던진다") {
                val variant = mockk<ProductVariant>()
                every { variantRepository.findById(1L) } returns Optional.of(variant)
                every { inventoryRepository.findByVariantId(1L) } returns null

                val command = AddCartItemCommand(userId = 1L, variantId = 1L, quantity = 1)

                val ex = shouldThrow<CartException> { service.addItem(command) }
                ex.errorCode shouldBe ErrorCode.INVENTORY_NOT_FOUND
            }
        }

        context("재고가 요청 수량보다 부족하면") {
            it("INSUFFICIENT_STOCK 예외를 던진다") {
                val variant = mockk<ProductVariant>()
                val inventory = Inventory.create(variantId = 1L, quantity = 2)

                every { variantRepository.findById(1L) } returns Optional.of(variant)
                every { inventoryRepository.findByVariantId(1L) } returns inventory

                val command = AddCartItemCommand(userId = 1L, variantId = 1L, quantity = 5)

                val ex = shouldThrow<CartException> { service.addItem(command) }
                ex.errorCode shouldBe ErrorCode.INSUFFICIENT_STOCK
            }
        }

        context("기존 장바구니가 있으면") {
            it("기존 장바구니에 아이템을 추가한다") {
                val variant = mockk<ProductVariant>()
                val inventory = Inventory.create(variantId = 1L, quantity = 10)
                val cart = Cart.create(userId = 1L)

                every { variantRepository.findById(1L) } returns Optional.of(variant)
                every { inventoryRepository.findByVariantId(1L) } returns inventory
                every { cartRepository.findByUserId(1L) } returns cart
                every { variant.finalPrice() } returns 5000L

                val command = AddCartItemCommand(userId = 1L, variantId = 1L, quantity = 2)
                val result = service.addItem(command)

                result shouldBe cart
                result.findActiveItems().size shouldBe 1
                verify(exactly = 0) { cartRepository.save(any()) } // 새로 저장하지 않음
            }
        }

        context("장바구니가 없으면") {
            it("새 장바구니를 생성 후 저장하고 아이템을 추가한다") {
                val variant = mockk<ProductVariant>()
                val inventory = Inventory.create(variantId = 1L, quantity = 10)

                every { variantRepository.findById(1L) } returns Optional.of(variant)
                every { inventoryRepository.findByVariantId(1L) } returns inventory
                every { cartRepository.findByUserId(1L) } returns null
                every { cartRepository.save(any()) } answers { firstArg() }
                every { variant.finalPrice() } returns 3000L

                val command = AddCartItemCommand(userId = 1L, variantId = 1L, quantity = 1)
                val result = service.addItem(command)

                result.userId shouldBe 1L
                verify(exactly = 1) { cartRepository.save(any()) }
            }
        }
    }

    describe("updateItem") {

        context("장바구니가 없으면") {
            it("CART_NOT_FOUND 예외를 던진다") {
                every { cartRepository.findByUserId(1L) } returns null

                val command = UpdateCartItemCommand(userId = 1L, cartItemId = 1L, quantity = 3)

                val ex = shouldThrow<CartException> { service.updateItem(command) }
                ex.errorCode shouldBe ErrorCode.CART_NOT_FOUND
            }
        }

        context("CartItem이 없으면") {
            it("CART_ITEM_NOT_FOUND 예외를 던진다") {
                val cart = mockk<Cart>()
                every { cartRepository.findByUserId(1L) } returns cart
                every { cartItemRepository.findById(99L) } returns Optional.empty()

                val command = UpdateCartItemCommand(userId = 1L, cartItemId = 99L, quantity = 3)

                val ex = shouldThrow<CartException> { service.updateItem(command) }
                ex.errorCode shouldBe ErrorCode.CART_ITEM_NOT_FOUND
            }
        }

        context("CartItem이 다른 사용자의 장바구니에 속하면") {
            it("예외를 던진다") {
                val myCart = mockk<Cart>()
                val otherCart = mockk<Cart>()
                val item = mockk<CartItem>()

                every { cartRepository.findByUserId(1L) } returns myCart
                every { cartItemRepository.findById(1L) } returns Optional.of(item)
                every { myCart.id } returns 1L
                every { item.cart } returns otherCart
                every { otherCart.id } returns 2L

                val command = UpdateCartItemCommand(userId = 1L, cartItemId = 1L, quantity = 3)

                shouldThrow<IllegalArgumentException> { service.updateItem(command) }
            }
        }

        context("정상 요청이면") {
            it("수량을 업데이트하고 장바구니를 반환한다") {
                val cart = mockk<Cart>()
                val item = mockk<CartItem>()

                every { cartRepository.findByUserId(1L) } returns cart
                every { cartItemRepository.findById(1L) } returns Optional.of(item)
                every { cart.id } returns 1L
                every { item.cart } returns cart
                every { item.updateQuantity(5) } returns Unit

                val command = UpdateCartItemCommand(userId = 1L, cartItemId = 1L, quantity = 5)
                val result = service.updateItem(command)

                result shouldBe cart
                verify(exactly = 1) { item.updateQuantity(5) }
            }
        }
    }

    describe("removeItem") {

        context("장바구니가 없으면") {
            it("CART_NOT_FOUND 예외를 던진다") {
                every { cartRepository.findByUserId(1L) } returns null

                val ex = shouldThrow<CartException> { service.removeItem(userId = 1L, cartItemId = 1L) }
                ex.errorCode shouldBe ErrorCode.CART_NOT_FOUND
            }
        }

        context("다른 사용자의 CartItem이면") {
            it("예외를 던진다") {
                val myCart = mockk<Cart>()
                val otherCart = mockk<Cart>()
                val item = mockk<CartItem>()

                every { cartRepository.findByUserId(1L) } returns myCart
                every { cartItemRepository.findById(1L) } returns Optional.of(item)
                every { myCart.id } returns 1L
                every { item.cart } returns otherCart
                every { otherCart.id } returns 2L

                shouldThrow<IllegalArgumentException> { service.removeItem(userId = 1L, cartItemId = 1L) }
            }
        }

        context("정상 요청이면") {
            it("아이템을 소프트 삭제한다") {
                val cart = mockk<Cart>()
                val item = mockk<CartItem>()

                every { cartRepository.findByUserId(1L) } returns cart
                every { cartItemRepository.findById(1L) } returns Optional.of(item)
                every { cart.id } returns 1L
                every { item.cart } returns cart
                every { item.softDelete() } returns Unit

                service.removeItem(userId = 1L, cartItemId = 1L)

                verify(exactly = 1) { item.softDelete() }
            }
        }
    }
})