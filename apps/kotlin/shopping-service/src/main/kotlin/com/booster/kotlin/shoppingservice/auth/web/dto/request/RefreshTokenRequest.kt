package com.booster.kotlin.shoppingservice.auth.web.dto.request

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank val refreshToken: String,
)
