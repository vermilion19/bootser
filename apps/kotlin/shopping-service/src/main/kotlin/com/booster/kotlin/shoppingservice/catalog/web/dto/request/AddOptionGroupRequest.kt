package com.booster.kotlin.shoppingservice.catalog.web.dto.request

import com.booster.kotlin.shoppingservice.catalog.application.dto.AddOptionGroupCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class AddOptionGroupRequest(
    @field:NotBlank val name: String,
    val displayOrder: Int = 0,
    @field:NotEmpty val optionValues: List<OptionValueItem>,
) {
    data class OptionValueItem(
        @field:NotBlank val value: String,
        val additionalPrice: Long = 0,
        val displayOrder: Int = 0,
    )

    fun toCommand(productId: Long) = AddOptionGroupCommand(
        productId = productId,
        name = name,
        displayOrder = displayOrder,
        optionValues = optionValues.map {
            AddOptionGroupCommand.OptionValueItem(it.value, it.additionalPrice, it.displayOrder)
        },
    )
}
