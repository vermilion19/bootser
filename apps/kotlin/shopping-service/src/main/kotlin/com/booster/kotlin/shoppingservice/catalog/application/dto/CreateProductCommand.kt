package com.booster.kotlin.shoppingservice.catalog.application.dto

data class CreateProductCommand(
    val name: String,
    val description: String,
    val basePrice: Long,
    val categoryId: Long,
)
