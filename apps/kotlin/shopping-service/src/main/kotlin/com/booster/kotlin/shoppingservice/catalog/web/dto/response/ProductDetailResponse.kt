package com.booster.kotlin.shoppingservice.catalog.web.dto.response

import com.booster.kotlin.shoppingservice.catalog.domain.Product

data class ProductDetailResponse(
    val id: Long,
    val name: String,
    val description: String,
    val basePrice: Long,
    val status: String,
    val categoryId: Long,
    val optionGroups: List<OptionGroupResponse>,
    val variants: List<VariantResponse>,
    val images: List<ImageResponse>,
) {
    data class OptionGroupResponse(
        val id: Long,
        val name: String,
        val displayOrder: Int,
        val optionValues: List<OptionValueResponse>,
    )

    data class OptionValueResponse(
        val id: Long,
        val value: String,
        val additionalPrice: Long,
        val displayOrder: Int,
    )

    data class VariantResponse(
        val id: Long,
        val sku: String,
        val additionalPrice: Long,
        val finalPrice: Long,
    )

    data class ImageResponse(
        val id: Long,
        val imageUrl: String,
        val displayOrder: Int,
        val isThumbnail: Boolean,
    )

    companion object {
        fun from(product: Product) = ProductDetailResponse(
            id = product.id,
            name = product.name,
            description = product.description,
            basePrice = product.basePrice,
            status = product.status.name,
            categoryId = product.category.id,
            optionGroups = product.optionGroups.map { g ->
                OptionGroupResponse(
                    id = g.id,
                    name = g.name,
                    displayOrder = g.displayOrder,
                    optionValues = g.optionValues.map { v ->
                        OptionValueResponse(v.id, v.value, v.additionalPrice, v.displayOrder)
                    }
                )
            },
            variants = product.variants.map { v ->
                VariantResponse(v.id, v.sku, v.additionalPrice, v.finalPrice())
            },
            images = product.images.map { i ->
                ImageResponse(i.id, i.imageUrl, i.displayOrder, i.isThumbnail)
            },
        )
    }
}