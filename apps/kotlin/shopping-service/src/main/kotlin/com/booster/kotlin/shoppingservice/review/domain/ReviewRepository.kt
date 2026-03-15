package com.booster.kotlin.shoppingservice.review.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewRepository : JpaRepository<Review, Long> {
    fun existsByOrderItemId(orderItemId: Long): Boolean
    fun findByProductIdAndIsHiddenFalseOrderByCreatedAtDesc(productId: Long, pageable: Pageable): Page<Review>
}
