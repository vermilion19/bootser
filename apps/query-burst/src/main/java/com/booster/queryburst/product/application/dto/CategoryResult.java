package com.booster.queryburst.product.application.dto;

public record CategoryResult(
        Long id,
        String name,
        Long parentId,
        int depth
) {
}
