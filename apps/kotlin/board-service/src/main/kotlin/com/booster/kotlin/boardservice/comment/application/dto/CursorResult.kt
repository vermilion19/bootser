package com.booster.kotlin.boardservice.comment.application.dto

import com.booster.kotlin.boardservice.comment.domain.Comment

data class CursorResult(
    val comments: List<Comment>,
    val nextCursor: Long?,
    val hasNext: Boolean,
) {
}