package com.booster.kotlin.boardservice.comment.web.dto.response

import com.booster.kotlin.boardservice.comment.domain.Comment

data class CommentResponse(
    val id: Long,
    val postId: Long,
    val content: String,
    val author: String,
    val parentId: Long?,
) {
    companion object {
        fun from(comment: Comment) = CommentResponse(
            id = comment.id,
            postId = comment.postId,
            content = comment.content,
            author = comment.author,
            parentId = comment.parentId,
        )
    }
}
