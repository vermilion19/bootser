package com.booster.queryburst.product.web;

import com.booster.queryburst.member.web.dto.response.CursorPageResponse;
import com.booster.queryburst.product.application.CategoryService;
import com.booster.queryburst.product.application.dto.CategoryResult;
import com.booster.queryburst.product.web.dto.request.CategoryCreateRequest;
import com.booster.queryburst.product.web.dto.response.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // v1: COUNT 쿼리 포함 페이지 기반 조회
    @GetMapping
    public ResponseEntity<Page<CategoryResponse>> getCategories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<CategoryResponse> result = categoryService.getCategories(pageable)
                .map(CategoryResponse::from);
        return ResponseEntity.ok(result);
    }

    // v2: COUNT 쿼리 없음, OFFSET 없는 커서 기반 조회
    @GetMapping("/v2")
    public ResponseEntity<CursorPageResponse<CategoryResponse>> getCategoriesByCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<CategoryResult> fetched = categoryService.getCategoriesByCursor(cursor, size);

        boolean hasNext = fetched.size() > size;
        List<CategoryResponse> content = fetched.stream()
                .limit(size)
                .map(CategoryResponse::from)
                .toList();
        Long nextCursor = hasNext ? content.getLast().id() : null;

        return ResponseEntity.ok(CursorPageResponse.of(content, hasNext, nextCursor));
    }

    @PostMapping
    public ResponseEntity<Void> createCategory(@RequestBody CategoryCreateRequest request) {
        Long categoryId = categoryService.createCategory(request.name(), request.parentId());
        return ResponseEntity.created(URI.create("/api/categories/" + categoryId)).build();
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}