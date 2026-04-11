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
        name = "analytics_daily_sales_summary",
        indexes = {
                @Index(name = "idx_analytics_daily_sales_date_category", columnList = "sales_date, category_id")
        }
)
public class DailySalesSummaryEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "sales_date", nullable = false)
    private LocalDate date;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    protected DailySalesSummaryEntity() {
    }

    public static DailySalesSummaryEntity create(LocalDate date, Long categoryId) {
        DailySalesSummaryEntity entity = new DailySalesSummaryEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.date = date;
        entity.categoryId = categoryId;
        return entity;
    }

    public void apply(long amount, int orderDelta) {
        totalAmount += amount;
        orderCount = Math.max(0, orderCount + orderDelta);
    }

    public LocalDate getDate() {
        return date;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public long getOrderCount() {
        return orderCount;
    }
}
