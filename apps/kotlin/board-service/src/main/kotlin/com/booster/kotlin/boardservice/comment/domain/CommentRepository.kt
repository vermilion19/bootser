package com.booster.kotlin.boardservice.comment.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommentRepository : JpaRepository<Comment, Long> {
    fun findAllByPostIdAndParentIdIsNull(postId: Long, pageable: Pageable): Page<Comment>
    fun findAllByParentId(parentId: Long): List<Comment>

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.postId = :postId")
    fun deleteAllByPostId(@Param("postId") postId: Long): Int

    fun findByPostIdAndParentIdIsNullAndIdLessThanOrderByIdDesc(
        postId: Long,
        lastId: Long,
        pageable: Pageable,
    ): List<Comment>

}

