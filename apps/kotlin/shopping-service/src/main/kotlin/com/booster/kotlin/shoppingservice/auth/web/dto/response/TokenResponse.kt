package com.booster.kotlin.shoppingservice.auth.web.dto.response

import com.booster.kotlin.shoppingservice.auth.application.dto.TokenResult

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        fun from(result: TokenResult) = TokenResponse(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
        )
    }
}
