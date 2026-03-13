package com.booster.kotlin.shoppingservice.cart.web.controller

import com.booster.kotlin.shoppingservice.cart.application.CartService
import com.booster.kotlin.shoppingservice.cart.web.dto.request.AddCartItemRequest
import com.booster.kotlin.shoppingservice.cart.web.dto.request.UpdateCartItemRequest
import com.booster.kotlin.shoppingservice.cart.web.dto.response.CartResponse
import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/cart")
class CartController(
    private val cartService: CartService,
) {

    @GetMapping
    fun getCart(
        @AuthenticationPrincipal principal: Long,
    ): ResponseEntity<ApiResponse<CartResponse>> {
        val cart = cartService.getCart(principal)
        return ResponseEntity.ok(ApiResponse.ok(CartResponse.from(cart)))
    }

    @PostMapping("/items")
    fun addItem(
        @AuthenticationPrincipal principal: Long,
        @RequestBody @Valid request: AddCartItemRequest,
    ): ResponseEntity<ApiResponse<CartResponse>> {
        val cart = cartService.addItem(request.toCommand(principal))
        return ResponseEntity.ok(ApiResponse.ok(CartResponse.from(cart)))
    }

    @PatchMapping("/items/{cartItemId}")
    fun updateItem(
        @AuthenticationPrincipal principal: Long,
        @PathVariable cartItemId: Long,
        @RequestBody @Valid request: UpdateCartItemRequest,
    ): ResponseEntity<ApiResponse<CartResponse>> {
        val cart = cartService.updateItem(request.toCommand(principal, cartItemId))
        return ResponseEntity.ok(ApiResponse.ok(CartResponse.from(cart)))
    }

    @DeleteMapping("/items/{cartItemId}")
    fun removeItem(
        @AuthenticationPrincipal principal: Long,
        @PathVariable cartItemId: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        cartService.removeItem(principal, cartItemId)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

}