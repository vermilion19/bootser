package com.booster.queryburst.statistics.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 상품별 일별 판매 통계 테이블 (CQRS Write 모델).
 *
 * StatisticsEventConsumer가 ORDER_CREATED/ORDER_CANCELED 이벤트를 소비하여
 * 이 테이블을 실시간으로 갱신한다.
 *
 * 활용:
 *   - 일별 상품 판매 랭킹 히스토리 (DB 영속 버전)
 *   - Redis 랭킹은 Near-Realtime이지만 휘발성 → DB에 일별 스냅샷 보관
 *   - 월별/기간별 상품 판매 트렌드 분석
 *
 * 인덱스:
 *   - (date, product_id) UNIQUE: UPSERT 기준 컬럼
 *   - (date, sold_count): 날짜별 판매량 순위 조회
 */
@Entity
@Table(
        name = "product_daily_sales",
        indexes = {
                @Index(name = "idx_product_daily_date_product", columnList = "date, product_id", unique = true),
                @Index(name = "idx_product_daily_date_sold_count", columnList = "date, sold_count")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDailySales extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Long productId;

    /** 해당 날짜 누적 판매 수량 */
    @Column(nullable = false)
    private Long soldCount;

    /** 해당 날짜 누적 매출 (unitPrice * quantity 합산) */
    @Column(nullable = false)
    private Long revenue;

    public static ProductDailySales create(LocalDate date, Long productId) {
        ProductDailySales sales = new ProductDailySales();
        sales.id = SnowflakeGenerator.nextId();
        sales.date = date;
        sales.productId = productId;
        sales.soldCount = 0L;
        sales.revenue = 0L;
        return sales;
    }

    public void addSales(int quantity, Long unitPrice) {
        this.soldCount += quantity;
        this.revenue += (long) quantity * unitPrice;
    }

    public void subtractSales(int quantity, Long unitPrice) {
        this.soldCount = Math.max(0, this.soldCount - quantity);
        this.revenue = Math.max(0, this.revenue - (long) quantity * unitPrice);
    }
}
