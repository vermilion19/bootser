package com.booster.queryburstmsa.catalog.web.dto;

import com.booster.queryburstmsa.catalog.domain.entity.CategoryEntity;

public record CategoryResponse(
        Long id,
        String name,
        Long parentId,
        int depth
) {
    public static CategoryResponse from(CategoryEntity entity) {
        return new CategoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getParentId(),
                entity.getDepth()
        );
    }
}
