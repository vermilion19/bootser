package com.booster.kotlin.boardservice.comment.domain

sealed class CommentResult {
    data class Success(val comment: Comment) : CommentResult()
    data class NotFound(val id: Long) : CommentResult()
    data class Forbidden(val id: Long) : CommentResult()
}

sealed class CommentDeleteResult {
    data object Deleted : CommentDeleteResult()
    data class NotFound(val id: Long) : CommentDeleteResult()
    data class Forbidden(val id: Long) : CommentDeleteResult()
}
