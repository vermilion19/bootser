package com.booster.queryburstmsa.catalog.web.dto;

public record CategoryCreateRequest(
        String name,
        Long parentId
) {
}
