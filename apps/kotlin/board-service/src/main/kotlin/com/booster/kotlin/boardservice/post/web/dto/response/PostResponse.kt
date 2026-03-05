package com.booster.kotlin.boardservice.post.web.dto.response

data class PostResponse(
    val id: Long,
    val title: String,
    val content: String,
    val author: String,
)
