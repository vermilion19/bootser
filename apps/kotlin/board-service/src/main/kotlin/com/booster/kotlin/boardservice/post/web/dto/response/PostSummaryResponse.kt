package com.booster.kotlin.boardservice.post.web.dto.response

import com.booster.kotlin.boardservice.post.domain.PostSummary
import com.booster.kotlin.boardservice.post.infrastructure.PostRow

data class PostSummaryResponse(
    val id: Long,
    val title: String,
    val author: String,
    val contentPreview: String? = null,
) {
    companion object {
        fun from(summary: PostSummary) = PostSummaryResponse(
            id = summary.id,
            title = summary.title,
            author = summary.author,
        )

        fun from(row: PostRow) = PostSummaryResponse(
            id = row.id,
            title = row.title,
            author = row.author,
            contentPreview = row.contentPreview,
        )
    }
}
