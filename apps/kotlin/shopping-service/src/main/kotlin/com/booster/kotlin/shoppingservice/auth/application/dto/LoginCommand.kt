package com.booster.kotlin.shoppingservice.auth.application.dto

data class LoginCommand(
    val email: String,
    val password: String,
)
