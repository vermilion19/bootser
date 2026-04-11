package com.booster.queryburstmsa.catalog.web;

import com.booster.queryburstmsa.catalog.application.CatalogService;
import com.booster.queryburstmsa.catalog.domain.ProductStatus;
import com.booster.queryburstmsa.catalog.web.dto.CategoryCreateRequest;
import com.booster.queryburstmsa.catalog.web.dto.CategoryResponse;
import com.booster.queryburstmsa.catalog.web.dto.ProductCreateRequest;
import com.booster.queryburstmsa.catalog.web.dto.ProductResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(catalogService.getProducts(cursor, categoryId, status, size));
    }

    @PostMapping("/products")
    public ResponseEntity<Void> createProduct(@RequestBody ProductCreateRequest request) {
        Long productId = catalogService.createProduct(request);
        return ResponseEntity.created(URI.create("/api/products/" + productId)).build();
    }

    @PatchMapping("/products/{productId}/price")
    public ResponseEntity<Void> updatePrice(@PathVariable Long productId, @RequestParam long price) {
        catalogService.updatePrice(productId, price);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/products/{productId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable Long productId, @RequestParam ProductStatus status) {
        catalogService.updateStatus(productId, status);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        catalogService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(catalogService.getCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<Void> createCategory(@RequestBody CategoryCreateRequest request) {
        Long categoryId = catalogService.createCategory(request);
        return ResponseEntity.created(URI.create("/api/categories/" + categoryId)).build();
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        catalogService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}
