package com.booster.kotlin.boardservice.comment.web.dto.response

data class CursorResponse<T>(
    val content: List<T>,
    val nextCursor: Long?,
    val hasNext: Boolean,
)