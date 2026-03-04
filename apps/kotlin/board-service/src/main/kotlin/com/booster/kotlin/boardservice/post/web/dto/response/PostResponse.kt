package com.booster.kotlin.boardservice.post.web.dto.response

import com.booster.kotlin.boardservice.post.domain.Post

data class PostResponse(
    val id: Long,
    val title: String,
    val content: String,
    val author: String
){
    companion object {
        fun from(post: Post): PostResponse {
            return PostResponse(
                id = post.id,
                title = post.title,
                content = post.content,
                author = post.author
            )
        }
    }
}
