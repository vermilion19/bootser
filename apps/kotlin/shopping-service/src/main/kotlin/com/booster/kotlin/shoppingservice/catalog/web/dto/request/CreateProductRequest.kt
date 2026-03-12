package com.booster.kotlin.shoppingservice.catalog.web.dto.request

import com.booster.kotlin.shoppingservice.catalog.application.dto.CreateProductCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CreateProductRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val description: String,
    @field:Positive val basePrice: Long,
    val categoryId: Long,
) {
    fun toCommand() = CreateProductCommand(name, description, basePrice, categoryId)
}
