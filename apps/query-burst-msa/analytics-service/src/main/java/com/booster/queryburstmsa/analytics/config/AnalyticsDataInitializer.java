package com.booster.queryburstmsa.analytics.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class AnalyticsDataInitializer {

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private final AtomicReference<String> status = new AtomicReference<>("대기 중");

    private final JdbcTemplate jdbcTemplate;

    @Value("${data.init.daily-sales-count:20000}")
    private int dailySalesCount;

    @Value("${data.init.product-daily-sales-count:300000}")
    private int productDailySalesCount;

    @Value("${data.init.batch-size:5000}")
    private int batchSize;

    public void initializeAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 적재가 진행 중입니다.");
        }
        CompletableFuture.runAsync(() -> {
            try {
                initialize();
            } finally {
                running.set(false);
            }
        });
    }

    private void initialize() {
        status.set("analytics 데이터 초기화 중");
        jdbcTemplate.execute("TRUNCATE TABLE analytics_processed_order_event, analytics_product_daily_sales, analytics_daily_sales_summary RESTART IDENTITY CASCADE");

        String dailySql = "INSERT INTO analytics_daily_sales_summary (id, sales_date, category_id, total_amount, order_count, created_at, updated_at) VALUES (?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        for (int offset = 0; offset < dailySalesCount; offset += batchSize) {
            List<Object[]> batch = new ArrayList<>(batchSize);
            int end = Math.min(offset + batchSize, dailySalesCount);
            for (int i = offset; i < end; i++) {
                long id = i + 1L;
                batch.add(new Object[]{
                        id,
                        LocalDate.now().minusDays(i % 365),
                        (id % 155L) + 1,
                        ((id % 10000) + 1) * 1000L,
                        (id % 200L) + 1,
                        now,
                        now
                });
            }
            jdbcTemplate.batchUpdate(dailySql, batch);
            status.set("analytics daily summary 적재 중: " + end + "/" + dailySalesCount);
        }

        String productSql = "INSERT INTO analytics_product_daily_sales (id, sales_date, product_id, sold_count, revenue, created_at, updated_at) VALUES (?,?,?,?,?,?,?)";
        for (int offset = 0; offset < productDailySalesCount; offset += batchSize) {
            List<Object[]> batch = new ArrayList<>(batchSize);
            int end = Math.min(offset + batchSize, productDailySalesCount);
            for (int i = offset; i < end; i++) {
                long id = i + 1L;
                int soldCount = (int) (id % 50) + 1;
                long unitPrice = ((id % 5000) + 1) * 100L;
                batch.add(new Object[]{
                        id,
                        LocalDate.now().minusDays(i % 365),
                        (id % 100_000L) + 1,
                        soldCount,
                        unitPrice * soldCount,
                        now,
                        now
                });
            }
            jdbcTemplate.batchUpdate(productSql, batch);
            status.set("analytics product daily 적재 중: " + end + "/" + productDailySalesCount);
        }

        status.set("완료");
    }
}
