package com.booster.queryburstmsa.catalog.domain.entity;

import com.booster.queryburstmsa.catalog.domain.ProductStatus;
import com.booster.queryburstmsa.catalog.lock.StaleFencingTokenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductEntityTest {

    @Test
    void reserve_updates_last_fence_token() {
        ProductEntity product = ProductEntity.create("p1", 1000L, 10, ProductStatus.ACTIVE, 1L, 1L);

        product.reserve(3, 10L);

        assertEquals(7, product.getStock());
        assertEquals(10L, product.getLastFenceToken());
    }

    @Test
    void reserve_rejects_stale_token() {
        ProductEntity product = ProductEntity.create("p1", 1000L, 10, ProductStatus.ACTIVE, 1L, 1L);
        product.reserve(3, 10L);

        assertThrows(StaleFencingTokenException.class, () -> product.reserve(1, 9L));
    }

    @Test
    void fallback_does_not_update_last_fence_token() {
        ProductEntity product = ProductEntity.create("p1", 1000L, 10, ProductStatus.ACTIVE, 1L, 1L);

        product.reserveFallback(2);
        product.restoreFallback(1);

        assertEquals(9, product.getStock());
        assertEquals(0L, product.getLastFenceToken());
    }
}
