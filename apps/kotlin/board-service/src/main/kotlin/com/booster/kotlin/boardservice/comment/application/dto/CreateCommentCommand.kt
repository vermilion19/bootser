package com.booster.kotlin.boardservice.comment.application.dto

data class CreateCommentCommand(
    val postId: Long,
    val content: String,
    val author: String,
    val parentId: Long? = null,
)
