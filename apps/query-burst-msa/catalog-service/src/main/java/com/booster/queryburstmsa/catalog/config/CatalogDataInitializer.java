package com.booster.queryburstmsa.catalog.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class CatalogDataInitializer {

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private final AtomicReference<String> status = new AtomicReference<>("대기 중");

    private final JdbcTemplate jdbcTemplate;

    @Value("${data.init.category-count:155}")
    private int categoryCount;

    @Value("${data.init.product-count:100000}")
    private int productCount;

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
        status.set("catalog 데이터 초기화 중");
        jdbcTemplate.execute("TRUNCATE TABLE inventory_reservation_item, inventory_reservation, catalog_product, catalog_category RESTART IDENTITY CASCADE");

        LocalDateTime now = LocalDateTime.now();
        String categorySql = "INSERT INTO catalog_category (id, name, parent_id, depth, created_at, updated_at) VALUES (?,?,?,?,?,?)";
        List<Object[]> categories = new ArrayList<>(categoryCount);
        for (long id = 1; id <= categoryCount; id++) {
            Long parentId = id <= 5 ? null : ((id - 1) % 5) + 1;
            int depth = id <= 5 ? 1 : (id <= 30 ? 2 : 3);
            categories.add(new Object[]{id, "카테고리_" + id, parentId, depth, now, now});
        }
        jdbcTemplate.batchUpdate(categorySql, categories);

        String productSql = "INSERT INTO catalog_product (id, name, price, stock, status, category_id, seller_id, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)";
        for (int offset = 0; offset < productCount; offset += batchSize) {
            List<Object[]> batch = new ArrayList<>(batchSize);
            int end = Math.min(offset + batchSize, productCount);
            for (int i = offset; i < end; i++) {
                long id = i + 1L;
                long price = ((id % 5000) + 1) * 100L;
                int stock = (int) (id % 1000) + 1;
                String statusValue = stock < 20 ? "SOLD_OUT" : "ACTIVE";
                long categoryIdValue = (id % Math.max(categoryCount, 1)) + 1;
                long sellerId = (id % 1_000_000L) + 1;
                batch.add(new Object[]{id, "상품_" + id, price, stock, statusValue, categoryIdValue, sellerId, now, now});
            }
            jdbcTemplate.batchUpdate(productSql, batch);
            status.set("catalog products 적재 중: " + end + "/" + productCount);
        }

        status.set("완료");
    }
}
