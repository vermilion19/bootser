package com.booster.queryburst.product.web.dto.response;

import com.booster.queryburst.product.application.dto.CategoryResult;

public record CategoryResponse(
        Long id,
        String name,
        Long parentId,
        int depth
) {
    public static CategoryResponse from(CategoryResult result) {
        return new CategoryResponse(
                result.id(),
                result.name(),
                result.parentId(),
                result.depth()
        );
    }
}