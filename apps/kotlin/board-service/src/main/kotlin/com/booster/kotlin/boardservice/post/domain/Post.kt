package com.booster.kotlin.boardservice.post.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "posts")
class Post (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    var title: String,
    var content: String,
    var author: String,
){
    companion object{
        fun create(title: String, content: String, author: String): Post {
            return Post(title = title, content = content, author = author)
        }
    }

    fun update(title: String, content: String) {
        this.title = title
        this.content = content
    }


}