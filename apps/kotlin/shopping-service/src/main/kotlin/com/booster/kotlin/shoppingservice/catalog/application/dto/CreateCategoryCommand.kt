package com.booster.kotlin.shoppingservice.catalog.application.dto

data class CreateCategoryCommand(
    val name: String,
    val parentId: Long?,
    )
