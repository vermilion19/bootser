package com.booster.kotlin.boardservice.post.application

import com.booster.kotlin.boardservice.post.domain.Post
import com.booster.kotlin.boardservice.post.domain.PostRepository
import com.booster.kotlin.boardservice.post.domain.PostSummary
import com.booster.kotlin.boardservice.post.exception.PostNotFoundException
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

    @Transactional(readOnly = true)
    fun getById(id: Long): Post {
        return postRepository.findById(id).orElseThrow { PostNotFoundException(id) }
    }

    @Transactional(readOnly = true)
    fun getAll(pageable: Pageable): Page<Post> {
        return postRepository.findAll(pageable)
    }

    fun update(id: Long, title: String, content: String): Post {
        val post = getById(id)
        post.update(title, content)
        return post
    }

    fun delete(id: Long) {
        postRepository.deleteById(id)
    }

    // --- JPA Native Query ---

    @Transactional(readOnly = true)
    fun findByAuthor(author: String): List<Post> {
        return postRepository.findAllByAuthor(author)
    }

    @Transactional(readOnly = true)
    fun searchByKeyword(keyword: String, pageable: Pageable): Page<Post> {
        return postRepository.searchByKeyword(keyword, pageable)
    }

    @Transactional(readOnly = true)
    fun findSummaryByAuthor(author: String): List<PostSummary> {
        return postRepository.findSummaryByAuthor(author)
    }

    @Transactional(readOnly = true)
    fun countByAuthor(author: String): Long {
        return postRepository.countByAuthor(author)
    }

    fun bulkUpdateAuthorWithJpa(oldAuthor: String, newAuthor: String): Int {
        return postRepository.bulkUpdateAuthor(oldAuthor, newAuthor)
    }

    // --- JdbcTemplate ---

    @Transactional(readOnly = true)
    fun findRecentPosts(limit: Int): List<PostRow> {
        return postJdbcRepository.findTopN(limit)
    }

    @Transactional(readOnly = true)
    fun searchByKeywordWithJdbc(keyword: String): List<PostRow> {
        return postJdbcRepository.searchByKeyword(keyword)
    }

    @Transactional(readOnly = true)
    fun countAllWithJdbc(): Long {
        return postJdbcRepository.countAll()
    }

    fun bulkUpdateAuthorWithJdbc(oldAuthor: String, newAuthor: String): Int {
        return postJdbcRepository.bulkUpdateAuthor(oldAuthor, newAuthor)
    }
}
