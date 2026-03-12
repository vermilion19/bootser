package com.booster.kotlin.shoppingservice.catalog.web.controller

import com.booster.kotlin.shoppingservice.catalog.application.CategoryService
import com.booster.kotlin.shoppingservice.catalog.web.dto.request.CreateCategoryRequest
import com.booster.kotlin.shoppingservice.catalog.web.dto.request.UpdateCategoryRequest
import com.booster.kotlin.shoppingservice.catalog.web.dto.response.CategoryResponse
import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CategoryController(
    private val categoryService: CategoryService,
) {
    @GetMapping("/api/v1/categories")
    fun getAll(): ResponseEntity<ApiResponse<List<CategoryResponse>>> {
        val categories = categoryService.getAll().map { CategoryResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.ok(categories))
    }

    @PostMapping("/admin/v1/categories")
    fun create(
        @RequestBody @Valid request: CreateCategoryRequest,
    ): ResponseEntity<ApiResponse<CategoryResponse>> {
        val category = categoryService.create(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(CategoryResponse.from(category)))
    }

    @PatchMapping("/admin/v1/categories/{categoryId}")
    fun update(
        @PathVariable categoryId: Long,
        @RequestBody @Valid request: UpdateCategoryRequest,
    ): ResponseEntity<ApiResponse<CategoryResponse>> {
        val category = categoryService.update(request.toCommand(categoryId))
        return ResponseEntity.ok(ApiResponse.ok(CategoryResponse.from(category)))
    }

}