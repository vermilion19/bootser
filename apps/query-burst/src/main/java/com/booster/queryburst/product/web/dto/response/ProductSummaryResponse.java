package com.booster.queryburst.product.web.dto.response;

import com.booster.queryburst.product.application.dto.ProductResult;
import com.booster.queryburst.product.domain.ProductStatus;

public record ProductSummaryResponse(
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
    public static ProductSummaryResponse from(ProductResult result) {
        return new ProductSummaryResponse(
                result.id(),
                result.name(),
                result.price(),
                result.stock(),
                result.status(),
                result.categoryId(),
                result.categoryName(),
                result.sellerId(),
                result.sellerName()
        );
    }
}