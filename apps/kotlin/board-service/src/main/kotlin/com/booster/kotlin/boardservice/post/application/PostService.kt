package com.booster.kotlin.boardservice.post.application

import com.booster.kotlin.boardservice.post.domain.Post
import com.booster.kotlin.boardservice.post.domain.PostRepository
import com.booster.kotlin.boardservice.post.exception.PostNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PostService(
    private val postRepository: PostRepository
) {

    fun create(title: String, content: String, author: String): Post {
        return postRepository.save(Post.create(title, content, author))
    }

    fun getById(id:Long): Post{
        return postRepository.findById(id).orElseThrow { PostNotFoundException(id) }
    }

    fun getAll(pageable: Pageable): Page<Post> {
        return postRepository.findAll(pageable)
    }

    fun update(id: Long, title: String, content: String): Post{
        val post = getById(id)
        post.update(title,content)
        return post
    }

    fun delete(id: Long){
        postRepository.deleteById(id)
    }

}