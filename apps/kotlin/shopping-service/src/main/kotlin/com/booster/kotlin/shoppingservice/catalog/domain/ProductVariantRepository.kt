package com.booster.kotlin.shoppingservice.catalog.domain

import org.springframework.data.jpa.repository.JpaRepository

interface ProductVariantRepository : JpaRepository<ProductVariant, Long> {
    fun findAllByProductId(productId: Long): List<ProductVariant>
}

