package com.booster.kotlin.shoppingservice.catalog.web.dto.response

import com.booster.kotlin.shoppingservice.catalog.domain.Product

data class ProductSummaryResponse(
    val id: Long,
    val name: String,
    val basePrice: Long,
    val status: String,
    val categoryId: Long,
    val thumbnailUrl: String?,
) {
    companion object {
        fun from(product: Product) = ProductSummaryResponse(
            id = product.id,
            name = product.name,
            basePrice = product.basePrice,
            status = product.status.name,
            categoryId = product.category.id,
            thumbnailUrl = product.images.find { it.isThumbnail }?.imageUrl,
        )
    }
}
