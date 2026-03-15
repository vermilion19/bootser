package com.booster.kotlin.shoppingservice.review.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.review.application.ReviewService
import com.booster.kotlin.shoppingservice.review.web.dto.request.CreateReviewRequest
import com.booster.kotlin.shoppingservice.review.web.dto.response.ReviewResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ReviewController(
    private val reviewService: ReviewService,
) {

    /** 리뷰 작성 (배송 완료 후에만 허용) */
    @PostMapping("/reviews")
    fun create(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: CreateReviewRequest,
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val review = reviewService.create(request.toCommand(userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ReviewResponse.from(review)))
    }

    /** 상품별 리뷰 목록 조회 */
    @GetMapping("/products/{productId}/reviews")
    fun getByProduct(
        @PathVariable productId: Long,
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<ApiResponse<Page<ReviewResponse>>> {
        val reviews = reviewService.getByProduct(productId, pageable).map { ReviewResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.ok(reviews))
    }
}
