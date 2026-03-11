package com.booster.kotlin.shoppingservice.user.web.dto.request

import com.booster.kotlin.shoppingservice.user.application.dto.CreateUserCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    @field:NotBlank val name: String,
    @field:NotBlank val phone: String,
) {
    fun toCommand() = CreateUserCommand(
        email = email,
        password = password,
        name = name,
        phone = phone,
    )
}
