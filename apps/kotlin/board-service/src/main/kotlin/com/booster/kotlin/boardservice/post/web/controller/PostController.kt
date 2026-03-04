package com.booster.kotlin.boardservice.post.web.controller

import com.booster.kotlin.boardservice.post.application.PostService
import com.booster.kotlin.boardservice.post.web.dto.request.CreatePostRequest
import com.booster.kotlin.boardservice.post.web.dto.request.UpdatePostRequest
import com.booster.kotlin.boardservice.post.web.dto.response.PostResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/posts")
class PostController(
    private val postService: PostService
) {

    @PostMapping
    fun create(@RequestBody request: CreatePostRequest): PostResponse {
        val post = postService.create(request.title,request.content,request.author)
        return PostResponse.from(post)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) : PostResponse{
        return PostResponse.from(postService.getById(id))
    }

    @GetMapping
    fun getAll(pageable: Pageable): Page<PostResponse>{
        return postService.getAll(pageable).map { PostResponse.from(it) }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: UpdatePostRequest): PostResponse {
        val post = postService.update(id, request.title, request.content)
        return PostResponse.from(post)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        postService.delete(id)
    }
}