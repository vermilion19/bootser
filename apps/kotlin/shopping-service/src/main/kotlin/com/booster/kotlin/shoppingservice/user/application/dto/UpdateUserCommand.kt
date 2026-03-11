package com.booster.kotlin.shoppingservice.user.application.dto

data class UpdateUserCommand(
    val userId: Long,
    val name: String,
    val phone: String,
)
