package com.booster.kotlin.shoppingservice.user.web.dto.request

import com.booster.kotlin.shoppingservice.user.application.dto.UpdateUserCommand
import jakarta.validation.constraints.NotBlank

data class UpdateProfileRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val phone: String,
) {
    fun toCommand(userId: Long) = UpdateUserCommand(
        userId = userId,
        name = name,
        phone = phone,
    )
}
