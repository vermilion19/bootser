package com.booster.queryburstmsa.catalog.web.dto;

import com.booster.queryburstmsa.catalog.domain.ProductStatus;
import com.booster.queryburstmsa.catalog.domain.entity.ProductEntity;

public record ProductResponse(
        Long id,
        String name,
        long price,
        int stock,
        ProductStatus status,
        Long categoryId,
        Long sellerId
) {
    public static ProductResponse from(ProductEntity entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getName(),
                entity.getPrice(),
                entity.getStock(),
                entity.getStatus(),
                entity.getCategoryId(),
                entity.getSellerId()
        );
    }
}
