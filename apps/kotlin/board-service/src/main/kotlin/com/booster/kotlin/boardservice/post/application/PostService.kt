package com.booster.kotlin.boardservice.post.application

import com.booster.kotlin.boardservice.post.domain.Post
import com.booster.kotlin.boardservice.post.domain.PostDeleteResult
import com.booster.kotlin.boardservice.post.domain.PostRepository
import com.booster.kotlin.boardservice.post.domain.PostResult
import com.booster.kotlin.boardservice.post.domain.PostSummary
import com.booster.kotlin.boardservice.post.infrastructure.PostJdbcRepository
import com.booster.kotlin.boardservice.post.infrastructure.PostRow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PostService(
    private val postRepository: PostRepository,
    private val postJdbcRepository: PostJdbcRepository,
) {

    fun create(title: String, content: String, author: String): Post {
        return postRepository.save(Post.create(title, content, author))
    }

    fun getById(id: Long): PostResult {
        val post = postRepository.findById(id).orElse(null)
            ?: return PostResult.NotFound(id)
        return PostResult.Success(post)
    }

    fun getAll(pageable: Pageable): Page<Post> {
        return postRepository.findAll(pageable)
    }

    fun update(id: Long, title: String, content: String): PostResult {
        val post = postRepository.findById(id).orElse(null)
            ?: return PostResult.NotFound(id)
        post.update(title, content)
        return PostResult.Success(post)
    }

    fun delete(id: Long): PostDeleteResult {
        if (!postRepository.existsById(id)) return PostDeleteResult.NotFound(id)
        postRepository.deleteById(id)
        return PostDeleteResult.Deleted
    }

    // --- JPA Native Query ---

    fun findByAuthor(author: String): List<Post> {
        return postRepository.findAllByAuthor(author)
    }

    fun searchByKeyword(keyword: String, pageable: Pageable): Page<Post> {
        return postRepository.searchByKeyword(keyword, pageable)
    }

    fun findSummaryByAuthor(author: String): List<PostSummary> {
        return postRepository.findSummaryByAuthor(author)
    }

    fun countByAuthor(author: String): Long {
        return postRepository.countByAuthor(author)
    }

    fun bulkUpdateAuthorWithJpa(oldAuthor: String, newAuthor: String): Int {
        return postRepository.bulkUpdateAuthor(oldAuthor, newAuthor)
    }

    // --- JdbcTemplate ---

    fun findRecentPosts(limit: Int): List<PostRow> {
        return postJdbcRepository.findTopN(limit)
    }

    fun searchByKeywordWithJdbc(keyword: String): List<PostRow> {
        return postJdbcRepository.searchByKeyword(keyword)
    }

    fun countAllWithJdbc(): Long {
        return postJdbcRepository.countAll()
    }

    fun bulkUpdateAuthorWithJdbc(oldAuthor: String, newAuthor: String): Int {
        return postJdbcRepository.bulkUpdateAuthor(oldAuthor, newAuthor)
    }
}
