package com.booster.kotlin.boardservice.post.web.dto.request

data class CreatePostRequest(
    val title: String,
    val content: String,
    val author: String
) {
}