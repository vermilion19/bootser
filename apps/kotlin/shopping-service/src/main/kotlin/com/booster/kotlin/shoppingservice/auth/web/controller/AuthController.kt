package com.booster.kotlin.shoppingservice.auth.web.controller

import com.booster.kotlin.shoppingservice.auth.application.AuthService
import com.booster.kotlin.shoppingservice.auth.web.dto.request.LoginRequest
import com.booster.kotlin.shoppingservice.auth.web.dto.request.RefreshTokenRequest
import com.booster.kotlin.shoppingservice.auth.web.dto.response.TokenResponse
import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.user.application.UserService
import com.booster.kotlin.shoppingservice.user.web.dto.request.SignupRequest
import com.booster.kotlin.shoppingservice.user.web.dto.response.UserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController (
    private val userService: UserService,
    private val authService: AuthService,
){
    @PostMapping("/signup")
    fun signup(
        @RequestBody @Valid request: SignupRequest,
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val user = userService.signup(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(UserResponse.from(user)))
    }

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val result = authService.login(request.toCommand())
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.from(result)))
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val result = authService.refresh(request.refreshToken)
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.from(result)))
    }

}