package com.booster.kotlin.shoppingservice.catalog.application.dto

data class UpdateCategoryCommand(
    val categoryId: Long,
    val name: String,
)
