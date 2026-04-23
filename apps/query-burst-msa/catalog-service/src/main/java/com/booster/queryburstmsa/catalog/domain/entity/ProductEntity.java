package com.booster.queryburstmsa.catalog.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburstmsa.catalog.domain.ProductStatus;
import com.booster.queryburstmsa.catalog.lock.StaleFencingTokenException;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "catalog_product",
        indexes = {
                @Index(name = "idx_catalog_product_category_id", columnList = "category_id"),
                @Index(name = "idx_catalog_product_seller_id", columnList = "seller_id"),
                @Index(name = "idx_catalog_product_status", columnList = "status"),
                @Index(name = "idx_catalog_product_price", columnList = "price"),
                @Index(name = "idx_catalog_product_category_status_price", columnList = "category_id, status, price")
        }
)
public class ProductEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "last_fence_token", nullable = false)
    private long lastFenceToken = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    protected ProductEntity() {
    }

    public static ProductEntity create(
            String name,
            long price,
            int stock,
            ProductStatus status,
            Long categoryId,
            Long sellerId
    ) {
        ProductEntity entity = new ProductEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.name = name;
        entity.price = price;
        entity.stock = stock;
        entity.status = status;
        entity.categoryId = categoryId;
        entity.sellerId = sellerId;
        return entity;
    }

    public void updatePrice(long price) {
        this.price = price;
    }

    public void updateStatus(ProductStatus status) {
        this.status = status;
    }

    public void reserve(int quantity, long fencingToken) {
        if (fencingToken <= this.lastFenceToken) {
            throw new StaleFencingTokenException(this.id, fencingToken, this.lastFenceToken);
        }
        reserveInternal(quantity);
        this.lastFenceToken = fencingToken;
    }

    public void reserveFallback(int quantity) {
        reserveInternal(quantity);
    }

    public void restore(int quantity, long fencingToken) {
        if (fencingToken <= this.lastFenceToken) {
            throw new StaleFencingTokenException(this.id, fencingToken, this.lastFenceToken);
        }
        restoreInternal(quantity);
        this.lastFenceToken = fencingToken;
    }

    public void restoreFallback(int quantity) {
        restoreInternal(quantity);
    }

    private void reserveInternal(int quantity) {
        if (stock < quantity) {
            throw new IllegalStateException("Insufficient stock.");
        }
        stock -= quantity;
        if (stock == 0) {
            status = ProductStatus.SOLD_OUT;
        }
    }

    private void restoreInternal(int quantity) {
        stock += quantity;
        if (status == ProductStatus.SOLD_OUT && stock > 0) {
            status = ProductStatus.ACTIVE;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public long getLastFenceToken() {
        return lastFenceToken;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getSellerId() {
        return sellerId;
    }
}
