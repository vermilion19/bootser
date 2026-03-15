package com.booster.kotlin.shoppingservice.review.application.dto

data class CreateReviewCommand(
    val userId: Long,
    val orderItemId: Long,
    val rating: Int,
    val content: String?,
)
