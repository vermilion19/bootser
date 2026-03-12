package com.booster.kotlin.shoppingservice.auth.web.dto.request

import com.booster.kotlin.shoppingservice.auth.application.dto.LoginCommand
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
){
    fun toCommand() = LoginCommand(email = email, password = password)
}
