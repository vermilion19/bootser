package com.booster.kotlin.shoppingservice.catalog.application.dto

data class UpdateProductCommand(
    val productId: Long,
    val name: String,
    val description: String,
    val basePrice: Long,
)
