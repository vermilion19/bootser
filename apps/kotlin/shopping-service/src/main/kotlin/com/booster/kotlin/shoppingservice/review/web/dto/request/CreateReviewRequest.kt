package com.booster.kotlin.shoppingservice.review.web.dto.request

import com.booster.kotlin.shoppingservice.review.application.dto.CreateReviewCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class CreateReviewRequest(
    @field:NotNull val orderItemId: Long,
    @field:Min(1) @field:Max(5) val rating: Int,
    val content: String?,
) {
    fun toCommand(userId: Long) = CreateReviewCommand(
        userId = userId,
        orderItemId = orderItemId,
        rating = rating,
        content = content,
    )
}
