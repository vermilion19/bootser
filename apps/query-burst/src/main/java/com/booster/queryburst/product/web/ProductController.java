package com.booster.queryburst.product.web;

import com.booster.queryburst.member.web.dto.response.CursorPageResponse;
import com.booster.queryburst.product.application.ProductService;
import com.booster.queryburst.product.application.dto.ProductCreateCommand;
import com.booster.queryburst.product.application.dto.ProductResult;
import com.booster.queryburst.product.domain.ProductStatus;
import com.booster.queryburst.product.web.dto.request.ProductCreateRequest;
import com.booster.queryburst.product.web.dto.response.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 커서 기반 상품 목록 조회.
     *
     * 필터: categoryId, status, minPrice, maxPrice
     * 정렬: id DESC (Snowflake ID → 최신순)
     *
     * 인덱스 힌트:
     * - categoryId + status → idx_product_category_status_price 활용
     * - status만 → idx_product_status (카디널리티 낮음 → 부분 인덱스 고려)
     * - minPrice/maxPrice → idx_product_price 활용
     */
    @GetMapping
    public ResponseEntity<CursorPageResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<ProductResult> fetched = productService.getProductsByCursor(
                cursor, categoryId, status, minPrice, maxPrice, size
        );

        boolean hasNext = fetched.size() > size;
        List<ProductSummaryResponse> content = fetched.stream()
                .limit(size)
                .map(ProductSummaryResponse::from)
                .toList();
        Long nextCursor = hasNext ? content.getLast().id() : null;

        return ResponseEntity.ok(CursorPageResponse.of(content, hasNext, nextCursor));
    }

    @PostMapping
    public ResponseEntity<Void> createProduct(@RequestBody ProductCreateRequest request) {
        Long productId = productService.createProduct(new ProductCreateCommand(
                request.name(),
                request.price(),
                request.stock(),
                request.status(),
                request.categoryId(),
                request.sellerId()
        ));
        return ResponseEntity.created(URI.create("/api/products/" + productId)).build();
    }

    @PatchMapping("/{productId}/price")
    public ResponseEntity<Void> updatePrice(
            @PathVariable Long productId,
            @RequestParam Long price
    ) {
        productService.updatePrice(productId, price);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{productId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long productId,
            @RequestParam ProductStatus status
    ) {
        productService.updateStatus(productId, status);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }
}