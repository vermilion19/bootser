package com.booster.kotlin.shoppingservice.user.web.dto.request

import com.booster.kotlin.shoppingservice.user.application.dto.AddAddressCommand
import jakarta.validation.constraints.NotBlank

data class AddAddressRequest(
    @field:NotBlank val label: String,
    @field:NotBlank val recipientName: String,
    @field:NotBlank val recipientPhone: String,
    @field:NotBlank val zipCode: String,
    @field:NotBlank val address1: String,
    val address2: String? = null,
    val isDefault: Boolean = false,
) {
    fun toCommand(userId: Long) = AddAddressCommand(
        userId = userId,
        label = label,
        recipientName = recipientName,
        recipientPhone = recipientPhone,
        zipCode = zipCode,
        address1 = address1,
        address2 = address2,
        isDefault = isDefault,
    )
}

