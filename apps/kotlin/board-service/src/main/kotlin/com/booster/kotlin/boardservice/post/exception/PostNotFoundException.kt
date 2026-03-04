package com.booster.kotlin.boardservice.post.exception

class PostNotFoundException(id: Long) : RuntimeException("Post not found: $id") {
}