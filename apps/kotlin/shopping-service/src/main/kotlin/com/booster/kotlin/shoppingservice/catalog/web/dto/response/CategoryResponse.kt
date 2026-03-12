package com.booster.kotlin.shoppingservice.catalog.web.dto.response

import com.booster.kotlin.shoppingservice.catalog.domain.Category

data class CategoryResponse(
    val id: Long,
    val name: String,
    val children: List<CategoryResponse>,

) {
    companion object {
        fun from(category: Category): CategoryResponse = CategoryResponse(
            id = category.id,
            name = category.name,
            children = category.children.map { from(it) },
        )
    }
}
