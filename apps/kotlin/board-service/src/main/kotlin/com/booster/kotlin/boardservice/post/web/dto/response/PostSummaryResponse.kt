package com.booster.kotlin.boardservice.post.web.dto.response

data class PostSummaryResponse(
    val id: Long,
    val title: String,
    val author: String,
    val contentPreview: String? = null,
)
