package com.booster.kotlin.shoppingservice.cart.application

import com.booster.kotlin.shoppingservice.cart.application.dto.AddCartItemCommand
import com.booster.kotlin.shoppingservice.cart.application.dto.UpdateCartItemCommand
import com.booster.kotlin.shoppingservice.cart.domain.Cart
import com.booster.kotlin.shoppingservice.cart.domain.CartItemRepository
import com.booster.kotlin.shoppingservice.cart.domain.CartRepository
import com.booster.kotlin.shoppingservice.cart.exception.CartException
import com.booster.kotlin.shoppingservice.catalog.domain.ProductVariantRepository
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CartService(
    private val cartRepository: CartRepository,
    private val cartItemRepository: CartItemRepository,
    private val variantRepository: ProductVariantRepository,
    private val inventoryRepository: InventoryRepository,
    ) {

    @Transactional(readOnly = true)
    fun getCart(userId: Long): Cart =
        cartRepository.findByUserId(userId) ?: Cart.create(userId)

    fun addItem(command: AddCartItemCommand): Cart {
        val variant = variantRepository.findById(command.variantId)
            .orThrow { CartException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND) }

        // 재고 확인
        val inventory = inventoryRepository.findByVariantId(command.variantId)
            ?: throw CartException(ErrorCode.INVENTORY_NOT_FOUND)
        if (inventory.quantity < command.quantity) throw CartException(ErrorCode.INSUFFICIENT_STOCK)

        val cart = cartRepository.findByUserId(command.userId)
            ?: cartRepository.save(Cart.create(command.userId))

        cart.addItem(command.variantId, command.quantity, variant.finalPrice())
        return cart
    }

    fun updateItem(command: UpdateCartItemCommand): Cart {
        val cart = cartRepository.findByUserId(command.userId)
            ?: throw CartException(ErrorCode.CART_NOT_FOUND)

        val item = cartItemRepository.findById(command.cartItemId)
            .orThrow { CartException(ErrorCode.CART_ITEM_NOT_FOUND) }

        require(item.cart.id == cart.id) { "본인의 장바구니 항목만 수정할 수 있습니다" }
        item.updateQuantity(command.quantity)
        return cart
    }

    fun removeItem(userId: Long, cartItemId: Long) {
        val cart = cartRepository.findByUserId(userId)
            ?: throw CartException(ErrorCode.CART_NOT_FOUND)

        val item = cartItemRepository.findById(cartItemId)
            .orThrow { CartException(ErrorCode.CART_ITEM_NOT_FOUND) }

        require(item.cart.id == cart.id) { "본인의 장바구니 항목만 삭제할 수 있습니다" }
        item.softDelete()
    }

}