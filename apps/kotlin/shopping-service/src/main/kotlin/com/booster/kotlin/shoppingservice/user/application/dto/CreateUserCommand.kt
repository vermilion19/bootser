package com.booster.kotlin.shoppingservice.user.application.dto

data class CreateUserCommand(
    val email: String,
    val password: String,
    val name: String,
    val phone: String,
)
