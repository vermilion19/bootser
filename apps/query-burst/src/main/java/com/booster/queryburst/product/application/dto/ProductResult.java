package com.booster.queryburst.product.application.dto;

import com.booster.queryburst.product.domain.ProductStatus;

public record ProductResult(
        Long id,
        String name,
        Long price,
        int stock,
        ProductStatus status,
        Long categoryId,
        String categoryName,
        Long sellerId,
        String sellerName
) {
}