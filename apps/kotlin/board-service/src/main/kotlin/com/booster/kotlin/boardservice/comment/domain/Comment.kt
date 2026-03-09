package com.booster.kotlin.boardservice.comment.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "comments")
class Comment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    val postId: Long,
    var content: String,
    val author: String,
    val parentId: Long? = null,
) {

    companion object {
        fun create(postId: Long, content: String, author: String): Comment {
            return Comment(postId = postId, content = content, author = author)
        }

        fun createReply(postId: Long, content: String, author: String, parentId: Long): Comment {
            return Comment(postId = postId, content = content, author = author, parentId = parentId)
        }
    }

    fun update(content: String) {
        this.content = content
    }

    fun isReply(): Boolean = parentId != null
}