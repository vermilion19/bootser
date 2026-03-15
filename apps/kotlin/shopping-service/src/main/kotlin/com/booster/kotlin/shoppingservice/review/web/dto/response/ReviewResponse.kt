package com.booster.kotlin.shoppingservice.review.web.dto.response

import com.booster.kotlin.shoppingservice.review.domain.Review
import java.time.LocalDateTime

data class ReviewResponse(
    val id: Long,
    val userId: Long,
    val variantId: Long,
    val productId: Long,
    val rating: Int,
    val content: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(review: Review) = ReviewResponse(
            id = review.id,
            userId = review.userId,
            variantId = review.variantId,
            productId = review.productId,
            rating = review.rating,
            content = review.content,
            createdAt = review.createdAt,
        )
    }
}
