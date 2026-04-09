package com.booster.queryburst.product.web.dto.request;

import com.booster.queryburst.product.domain.ProductStatus;

public record ProductCreateRequest(
        String name,
        Long price,
        int stock,
        ProductStatus status,
        Long categoryId,
        Long sellerId
) {
}