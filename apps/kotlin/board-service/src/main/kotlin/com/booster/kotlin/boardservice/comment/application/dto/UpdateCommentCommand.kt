package com.booster.kotlin.boardservice.comment.application.dto

data class UpdateCommentCommand(
    val commentId: Long,
    val content: String,
    val author: String,
)
