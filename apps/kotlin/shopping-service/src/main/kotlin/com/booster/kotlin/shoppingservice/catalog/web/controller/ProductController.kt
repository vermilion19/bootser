package com.booster.kotlin.shoppingservice.catalog.web.controller

import com.booster.kotlin.shoppingservice.catalog.application.ProductService
import org.springframework.web.bind.annotation.RestController
import com.booster.kotlin.shoppingservice.catalog.web.dto.request.AddOptionGroupRequest
import com.booster.kotlin.shoppingservice.catalog.web.dto.request.CreateProductRequest
import com.booster.kotlin.shoppingservice.catalog.web.dto.request.UpdateProductRequest
import com.booster.kotlin.shoppingservice.catalog.web.dto.response.ProductDetailResponse
import com.booster.kotlin.shoppingservice.catalog.web.dto.response.ProductSummaryResponse
import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
class ProductController(
    private val productService: ProductService,
) {

    @GetMapping("/api/v1/products")
    fun getAll(
        @RequestParam(required = false) categoryId: Long?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> {
        val products = productService.getAll(categoryId, pageable).map { ProductSummaryResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.ok(products))
    }

    @GetMapping("/api/v1/products/{productId}")
    fun getOne(
        @PathVariable productId: Long,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val product = productService.getById(productId)
        return ResponseEntity.ok(ApiResponse.ok(ProductDetailResponse.from(product)))
    }

    @PostMapping("/admin/v1/products")
    fun create(
        @RequestBody @Valid request: CreateProductRequest,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val product = productService.create(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ProductDetailResponse.from(product)))
    }

    @PatchMapping("/admin/v1/products/{productId}")
    fun update(
        @PathVariable productId: Long,
        @RequestBody @Valid request: UpdateProductRequest,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val product = productService.update(request.toCommand(productId))
        return ResponseEntity.ok(ApiResponse.ok(ProductDetailResponse.from(product)))
    }

    @PostMapping("/admin/v1/products/{productId}/options")
    fun addOptionGroup(
        @PathVariable productId: Long,
        @RequestBody @Valid request: AddOptionGroupRequest,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val product = productService.addOptionGroup(request.toCommand(productId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ProductDetailResponse.from(product)))
    }

}