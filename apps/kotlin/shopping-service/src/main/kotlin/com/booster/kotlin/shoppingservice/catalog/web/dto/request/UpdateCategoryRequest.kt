package com.booster.kotlin.shoppingservice.catalog.web.dto.request

import com.booster.kotlin.shoppingservice.catalog.application.dto.UpdateCategoryCommand
import jakarta.validation.constraints.NotBlank

data class UpdateCategoryRequest(
    @field:NotBlank val name: String,
) {
    fun toCommand(categoryId: Long) = UpdateCategoryCommand(categoryId = categoryId, name = name)
}
