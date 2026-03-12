package com.booster.kotlin.shoppingservice.user.web.dto.response

import com.booster.kotlin.shoppingservice.user.domain.UserAddress

data class AddressResponse(
    val id: Long,
    val label: String,
    val recipientName: String,
    val recipientPhone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val isDefault: Boolean,
) {
    companion object {
        fun from(address: UserAddress) = AddressResponse(
            id = address.id,
            label = address.label,
            recipientName = address.recipientName,
            recipientPhone = address.recipientPhone,
            zipCode = address.zipCode,
            address1 = address.address1,
            address2 = address.address2,
            isDefault = address.isDefault,
        )
    }
}
