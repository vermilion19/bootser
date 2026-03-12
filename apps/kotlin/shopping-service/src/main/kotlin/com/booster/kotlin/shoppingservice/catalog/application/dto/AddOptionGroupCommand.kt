package com.booster.kotlin.shoppingservice.catalog.application.dto

data class AddOptionGroupCommand(
    val productId: Long,
    val name: String,
    val displayOrder: Int,
    val optionValues: List<OptionValueItem>,
) {
    data class OptionValueItem(
        val value: String,
        val additionalPrice: Long,
        val displayOrder: Int,
    )
}
