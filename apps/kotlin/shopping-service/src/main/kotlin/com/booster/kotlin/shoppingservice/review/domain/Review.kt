package com.booster.kotlin.shoppingservice.review.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "reviews",
    uniqueConstraints = [UniqueConstraint(name = "uq_review_order_item", columnNames = ["order_item_id"])],
)
class Review(
    @Column(name = "order_item_id", nullable = false) val orderItemId: Long,
    @Column(nullable = false) val userId: Long,
    @Column(nullable = false) val variantId: Long,
    @Column(nullable = false) val productId: Long,
    @Column(nullable = false) val rating: Int,
    @Column(columnDefinition = "TEXT") val content: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var isHidden: Boolean = false
        private set

    fun hide() {
        isHidden = true
    }

    fun show() {
        isHidden = false
    }

    companion object {
        fun create(
            orderItemId: Long,
            userId: Long,
            variantId: Long,
            productId: Long,
            rating: Int,
            content: String?,
        ) = Review(orderItemId, userId, variantId, productId, rating, content)
    }
}
