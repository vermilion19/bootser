package com.booster.kotlin.shoppingservice.user.web.dto.response

import com.booster.kotlin.shoppingservice.user.domain.User

data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val phone: String,
    val status: String,
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            phone = user.phone,
            status = user.status.name,
        )
    }
}
