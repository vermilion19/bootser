package com.booster.kotlin.boardservice.post.web.controller

import com.booster.kotlin.boardservice.post.application.PostService
import com.booster.kotlin.boardservice.post.web.toResponse
import com.booster.kotlin.boardservice.post.web.toResponseEntity
import com.booster.kotlin.boardservice.post.web.toSummaryResponse
import com.booster.kotlin.boardservice.post.web.dto.request.CreatePostRequest
import com.booster.kotlin.boardservice.post.web.dto.request.UpdatePostRequest
import com.booster.kotlin.boardservice.post.web.dto.response.PostResponse
import com.booster.kotlin.boardservice.post.web.dto.response.PostSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/posts")
class PostController(
    private val postService: PostService,
) {

    @PostMapping
    fun create(@RequestBody request: CreatePostRequest): PostResponse =
        postService.create(request.title, request.content, request.author).toResponse()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<PostResponse> =
        postService.getById(id).toResponseEntity()

    @GetMapping
    fun getAll(pageable: Pageable): Page<PostResponse> =
        postService.getAll(pageable).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: UpdatePostRequest): ResponseEntity<PostResponse> =
        postService.update(id, request.title, request.content).toResponseEntity()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Unit> =
        postService.delete(id).toResponseEntity()

    // =========================================================================
    // JPA Native Query 엔드포인트
    // =========================================================================

    @GetMapping("/search")
    fun searchByKeyword(@RequestParam keyword: String, pageable: Pageable): Page<PostResponse> =
        postService.searchByKeyword(keyword, pageable).toResponse()

    @GetMapping("/author/{author}")
    fun findByAuthor(@PathVariable author: String): List<PostResponse> =
        postService.findByAuthor(author).toResponse()

    @GetMapping("/author/{author}/summaries")
    fun findSummaryByAuthor(@PathVariable author: String): List<PostSummaryResponse> =
        postService.findSummaryByAuthor(author).map { it.toSummaryResponse() }

    @GetMapping("/author/{author}/count")
    fun countByAuthor(@PathVariable author: String): Map<String, Long> =
        mapOf("count" to postService.countByAuthor(author))

    // =========================================================================
    // JdbcTemplate 엔드포인트
    // =========================================================================

    @GetMapping("/jdbc/recent")
    fun findRecentPosts(@RequestParam(defaultValue = "5") limit: Int): List<PostSummaryResponse> =
        postService.findRecentPosts(limit).map { it.toSummaryResponse() }

    @GetMapping("/jdbc/search")
    fun searchByKeywordWithJdbc(@RequestParam keyword: String): List<PostSummaryResponse> =
        postService.searchByKeywordWithJdbc(keyword).map { it.toSummaryResponse() }

    @GetMapping("/jdbc/count")
    fun countAllWithJdbc(): Map<String, Long> =
        mapOf("count" to postService.countAllWithJdbc())
}
