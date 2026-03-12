package com.booster.kotlin.shoppingservice.user.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.user.application.UserService
import com.booster.kotlin.shoppingservice.user.web.dto.request.AddAddressRequest
import com.booster.kotlin.shoppingservice.user.web.dto.request.UpdateProfileRequest
import com.booster.kotlin.shoppingservice.user.web.dto.response.AddressResponse
import com.booster.kotlin.shoppingservice.user.web.dto.response.UserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal userId: Long): ResponseEntity<ApiResponse<UserResponse>> {
        val user = userService.getById(userId)
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)))
    }

    @PatchMapping("/me")
    fun updateMe(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: UpdateProfileRequest,
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val user = userService.updateProfile(request.toCommand(userId))
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)))
    }

    @GetMapping("/me/addresses")
    fun getAddresses(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<List<AddressResponse>>> {
        val addresses = userService.getAddresses(userId)
        return ResponseEntity.ok(ApiResponse.ok(addresses.map { AddressResponse.from(it) }))
    }

    @PostMapping("/me/addresses")
    fun addAddress(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: AddAddressRequest,
    ): ResponseEntity<ApiResponse<AddressResponse>> {
        val address = userService.addAddress(request.toCommand(userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(AddressResponse.from(address)))
    }

}