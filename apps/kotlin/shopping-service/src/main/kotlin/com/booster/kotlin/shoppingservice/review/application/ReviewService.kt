package com.booster.kotlin.shoppingservice.review.application

import com.booster.kotlin.shoppingservice.catalog.domain.ProductVariantRepository
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import com.booster.kotlin.shoppingservice.order.domain.OrderItemRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import com.booster.kotlin.shoppingservice.order.exception.OrderException
import com.booster.kotlin.shoppingservice.review.application.dto.CreateReviewCommand
import com.booster.kotlin.shoppingservice.review.domain.Review
import com.booster.kotlin.shoppingservice.review.domain.ReviewRepository
import com.booster.kotlin.shoppingservice.review.exception.ReviewException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderRepository: OrderRepository,
    private val variantRepository: ProductVariantRepository,
) {

    fun create(command: CreateReviewCommand): Review {
        val orderItem = orderItemRepository.findById(command.orderItemId)
            .orThrow { ReviewException(ErrorCode.ORDER_NOT_FOUND) }

        val order = orderRepository.findById(orderItem.order.id)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }

        // 본인 주문 여부 검증
        if (order.userId != command.userId) {
            throw ReviewException(ErrorCode.REVIEW_PERMISSION_DENIED)
        }

        // 배송 완료 여부 검증
        if (order.status != OrderStatus.DELIVERED) {
            throw ReviewException(ErrorCode.REVIEW_NOT_ALLOWED)
        }

        // 중복 리뷰 방지
        if (reviewRepository.existsByOrderItemId(command.orderItemId)) {
            throw ReviewException(ErrorCode.REVIEW_ALREADY_EXISTS)
        }

        val variant = variantRepository.findById(orderItem.variantId)
            .orThrow { ReviewException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND) }

        val review = reviewRepository.save(
            Review.create(
                orderItemId = command.orderItemId,
                userId = command.userId,
                variantId = orderItem.variantId,
                productId = variant.product.id,
                rating = command.rating,
                content = command.content,
            )
        )

        return review
    }

    @Transactional(readOnly = true)
    fun getByProduct(productId: Long, pageable: Pageable): Page<Review> =
        reviewRepository.findByProductIdAndIsHiddenFalseOrderByCreatedAtDesc(productId, pageable)

    fun hide(reviewId: Long): Review {
        val review = reviewRepository.findById(reviewId)
            .orThrow { ReviewException(ErrorCode.REVIEW_NOT_FOUND) }
        review.hide()
        return review
    }

    fun show(reviewId: Long): Review {
        val review = reviewRepository.findById(reviewId)
            .orThrow { ReviewException(ErrorCode.REVIEW_NOT_FOUND) }
        review.show()
        return review
    }
}
