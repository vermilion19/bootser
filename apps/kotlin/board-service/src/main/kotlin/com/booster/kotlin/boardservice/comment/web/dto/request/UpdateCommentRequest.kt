package com.booster.kotlin.boardservice.comment.web.dto.request

import com.booster.kotlin.boardservice.comment.application.dto.UpdateCommentCommand

data class UpdateCommentRequest(
    val content: String,
    val author: String,
) {
    fun toCommand(commentId: Long) = UpdateCommentCommand(commentId, content, author)
}
