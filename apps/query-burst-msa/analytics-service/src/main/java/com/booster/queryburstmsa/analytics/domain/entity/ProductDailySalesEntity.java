package com.booster.queryburstmsa.analytics.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(
        name = "analytics_product_daily_sales",
        indexes = {
                @Index(name = "idx_analytics_product_daily_date_sold", columnList = "sales_date, sold_count"),
                @Index(name = "idx_analytics_product_daily_product_date", columnList = "product_id, sales_date")
        }
)
public class ProductDailySalesEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "sales_date", nullable = false)
    private LocalDate date;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    @Column(nullable = false)
    private long revenue;

    protected ProductDailySalesEntity() {
    }

    public static ProductDailySalesEntity create(LocalDate date, Long productId) {
        ProductDailySalesEntity entity = new ProductDailySalesEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.date = date;
        entity.productId = productId;
        return entity;
    }

    public void apply(int quantityDelta, long revenueDelta) {
        soldCount = Math.max(0, soldCount + quantityDelta);
        revenue += revenueDelta;
    }

    public LocalDate getDate() {
        return date;
    }

    public Long getProductId() {
        return productId;
    }

    public int getSoldCount() {
        return soldCount;
    }

    public long getRevenue() {
        return revenue;
    }
}
