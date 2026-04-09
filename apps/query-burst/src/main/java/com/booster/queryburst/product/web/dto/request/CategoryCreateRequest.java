package com.booster.queryburst.product.web.dto.request;

public record CategoryCreateRequest(
        String name,
        Long parentId  // null이면 대분류(root)로 생성
) {
}