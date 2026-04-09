package com.booster.queryburst.product.application.dto;

import com.booster.queryburst.product.domain.ProductStatus;

public record ProductCreateCommand(
        String name,
        Long price,
        int stock,
        ProductStatus status,
        Long categoryId,
        Long sellerId
) {
}