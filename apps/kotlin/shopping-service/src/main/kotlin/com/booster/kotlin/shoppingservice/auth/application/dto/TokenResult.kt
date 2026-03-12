package com.booster.kotlin.shoppingservice.auth.application.dto

data class TokenResult(
    val accessToken: String,
    val refreshToken: String,
)
