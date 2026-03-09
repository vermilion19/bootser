package com.booster.kotlin.boardservice.comment.web.dto.request

import com.booster.kotlin.boardservice.comment.application.dto.CreateCommentCommand

data class CreateCommentRequest(
    val postId: Long,
    val content: String,
    val author: String,
){
    fun toCommand() = CreateCommentCommand(postId, content, author)
}
