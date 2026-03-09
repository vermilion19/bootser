package com.booster.kotlin.boardservice.comment.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommentRepository : JpaRepository<Comment, Long> {
    fun findAllByPostId(postId: Long): List<Comment>

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.postId = :postId")
    fun deleteAllByPostId(@Param("postId") postId: Long): Int
}

