package com.booster.kotlin.boardservice.post.web.controller

import com.booster.kotlin.boardservice.post.application.PostService
import com.booster.kotlin.boardservice.post.web.dto.request.CreatePostRequest
import com.booster.kotlin.boardservice.post.web.dto.request.UpdatePostRequest
import com.booster.kotlin.boardservice.post.web.dto.response.PostResponse
import com.booster.kotlin.boardservice.post.web.dto.response.PostSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    private val postService: PostService
) {

    @PostMapping
    fun create(@RequestBody request: CreatePostRequest): PostResponse {
        val post = postService.create(request.title, request.content, request.author)
        return PostResponse.from(post)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): PostResponse {
        return PostResponse.from(postService.getById(id))
    }

    @GetMapping
    fun getAll(pageable: Pageable): Page<PostResponse> {
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

    // =========================================================================
    // JPA Native Query 엔드포인트
    // =========================================================================

    // GET /posts/search?keyword=스프링&page=0&size=10
    @GetMapping("/search")
    fun searchByKeyword(
        @RequestParam keyword: String,
        pageable: Pageable,
    ): Page<PostResponse> {
        return postService.searchByKeyword(keyword, pageable).map { PostResponse.from(it) }
    }

    // GET /posts/author/홍길동  → 전체 엔티티 조회
    @GetMapping("/author/{author}")
    fun findByAuthor(@PathVariable author: String): List<PostResponse> {
        return postService.findByAuthor(author).map { PostResponse.from(it) }
    }

    // GET /posts/author/홍길동/summaries  → Interface Projection (id, title, author만)
    @GetMapping("/author/{author}/summaries")
    fun findSummaryByAuthor(@PathVariable author: String): List<PostSummaryResponse> {
        return postService.findSummaryByAuthor(author).map { PostSummaryResponse.from(it) }
    }

    // GET /posts/author/홍길동/count
    @GetMapping("/author/{author}/count")
    fun countByAuthor(@PathVariable author: String): Map<String, Long> {
        return mapOf("count" to postService.countByAuthor(author))
    }

    // =========================================================================
    // JdbcTemplate 엔드포인트
    // =========================================================================

    // GET /posts/jdbc/recent?limit=5
    @GetMapping("/jdbc/recent")
    fun findRecentPosts(@RequestParam(defaultValue = "5") limit: Int): List<PostSummaryResponse> {
        return postService.findRecentPosts(limit).map { PostSummaryResponse.from(it) }
    }

    // GET /posts/jdbc/search?keyword=스프링
    @GetMapping("/jdbc/search")
    fun searchByKeywordWithJdbc(@RequestParam keyword: String): List<PostSummaryResponse> {
        return postService.searchByKeywordWithJdbc(keyword).map { PostSummaryResponse.from(it) }
    }

    // GET /posts/jdbc/count
    @GetMapping("/jdbc/count")
    fun countAllWithJdbc(): Map<String, Long> {
        return mapOf("count" to postService.countAllWithJdbc())
    }
}
