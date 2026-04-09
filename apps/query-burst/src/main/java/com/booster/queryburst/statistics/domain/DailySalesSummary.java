package com.booster.queryburst.statistics.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 카테고리별 일별 매출 집계 테이블 (CQRS Write 모델).
 *
 * StatisticsEventConsumer가 ORDER_CREATED/ORDER_CANCELED 이벤트를 소비하여
 * 이 테이블을 실시간으로 갱신한다.
 *
 * 목적:
 *   기존: SELECT SUM(total_amount) ... FROM orders JOIN order_item JOIN product
 *         → 3,000만 건 테이블에 집계 쿼리 → 수 초 소요
 *   개선: SELECT * FROM daily_sales_summary WHERE date = ? AND category_id = ?
 *         → 비정규화 단순 조회 → 5ms 이내
 *
 * 인덱스:
 *   - (date, category_id) UNIQUE: UPSERT 기준 컬럼
 */
@Entity
@Table(
        name = "daily_sales_summary",
        indexes = {
                @Index(name = "idx_daily_sales_date_category", columnList = "date, category_id", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySalesSummary extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long orderCount;

    public static DailySalesSummary create(LocalDate date, Long categoryId) {
        DailySalesSummary summary = new DailySalesSummary();
        summary.id = SnowflakeGenerator.nextId();
        summary.date = date;
        summary.categoryId = categoryId;
        summary.totalAmount = 0L;
        summary.orderCount = 0L;
        return summary;
    }

    public void addSales(Long amount) {
        this.totalAmount += amount;
        this.orderCount++;
    }

    public void subtractSales(Long amount) {
        this.totalAmount = Math.max(0, this.totalAmount - amount);
        this.orderCount = Math.max(0, this.orderCount - 1);
    }
}
