package com.booster.kotlin.shoppingservice.user.application.dto

data class AddAddressCommand(
    val userId: Long,
    val label: String,
    val recipientName: String,
    val recipientPhone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val isDefault: Boolean,

)
