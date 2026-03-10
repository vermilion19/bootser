package com.booster.kotlin.boardservice.post.domain

import com.booster.kotlin.boardservice.tag.domain.PostTag
import com.booster.kotlin.boardservice.tag.domain.Tag
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "posts")
class Post (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    var title: String,
    var content: String,
    var author: String,

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    val postTags: MutableList<PostTag> = mutableListOf()
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

    fun addTag(tag: Tag) {
        val postTag = PostTag.create(this, tag)
        postTags.add(postTag)
    }

    fun removeTag(tag: Tag) {
        postTags.removeIf { it.tag.id == tag.id }
    }



}