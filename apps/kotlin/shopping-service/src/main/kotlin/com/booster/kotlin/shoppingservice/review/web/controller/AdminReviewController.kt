package com.booster.kotlin.shoppingservice.review.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.review.application.ReviewService
import com.booster.kotlin.shoppingservice.review.web.dto.response.ReviewResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/v1/reviews")
class AdminReviewController(
    private val reviewService: ReviewService,
) {

    /** 리뷰 숨김 처리 */
    @PatchMapping("/{reviewId}/hide")
    fun hide(
        @PathVariable reviewId: Long,
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val review = reviewService.hide(reviewId)
        return ResponseEntity.ok(ApiResponse.ok(ReviewResponse.from(review)))
    }

    /** 리뷰 공개 처리 */
    @PatchMapping("/{reviewId}/show")
    fun show(
        @PathVariable reviewId: Long,
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val review = reviewService.show(reviewId)
        return ResponseEntity.ok(ApiResponse.ok(ReviewResponse.from(review)))
    }
}
