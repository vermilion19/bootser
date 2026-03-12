package com.booster.kotlin.shoppingservice.catalog.web.dto.request

import com.booster.kotlin.shoppingservice.catalog.application.dto.UpdateProductCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class UpdateProductRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val description: String,
    @field:Positive val basePrice: Long,
) {
    fun toCommand(productId: Long) = UpdateProductCommand(productId, name, description, basePrice)
}
