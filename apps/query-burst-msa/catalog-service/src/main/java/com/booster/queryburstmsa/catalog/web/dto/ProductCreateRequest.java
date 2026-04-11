package com.booster.queryburstmsa.catalog.web.dto;

import com.booster.queryburstmsa.catalog.domain.ProductStatus;

public record ProductCreateRequest(
        String name,
        long price,
        int stock,
        ProductStatus status,
        Long categoryId,
        Long sellerId
) {
}
