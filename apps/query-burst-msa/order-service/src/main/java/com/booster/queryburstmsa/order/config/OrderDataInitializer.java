package com.booster.queryburstmsa.order.config;

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
public class OrderDataInitializer {

    private static final String[] ORDER_STATUSES = {"STOCK_RESERVED", "PAID", "SHIPPED", "DELIVERED", "CANCELED", "REJECTED"};

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private final AtomicReference<String> status = new AtomicReference<>("대기 중");

    private final JdbcTemplate jdbcTemplate;

    @Value("${data.init.order-count:3000000}")
    private int orderCount;

    @Value("${data.init.order-item-per-order:3}")
    private int orderItemPerOrder;

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
        status.set("order 데이터 초기화 중");
        jdbcTemplate.execute("TRUNCATE TABLE customer_order_item, order_outbox_event, customer_order RESTART IDENTITY CASCADE");

        LocalDateTime now = LocalDateTime.now();
        String orderSql = "INSERT INTO customer_order (id, member_id, status, total_amount, reservation_id, ordered_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)";
        for (int offset = 0; offset < orderCount; offset += batchSize) {
            List<Object[]> batch = new ArrayList<>(batchSize);
            int end = Math.min(offset + batchSize, orderCount);
            for (int i = offset; i < end; i++) {
                long id = i + 1L;
                batch.add(new Object[]{
                        id,
                        (id % 1_000_000L) + 1,
                        ORDER_STATUSES[i % ORDER_STATUSES.length],
                        ((id % 5000) + 1) * 1000L,
                        "resv-" + id,
                        now.minusMinutes(id % 100_000),
                        now,
                        now
                });
            }
            jdbcTemplate.batchUpdate(orderSql, batch);
            status.set("orders 적재 중: " + end + "/" + orderCount);
        }

        String itemSql = "INSERT INTO customer_order_item (id, order_id, product_id, category_id, quantity, unit_price) VALUES (?,?,?,?,?,?)";
        int totalItemCount = orderCount * orderItemPerOrder;
        for (int offset = 0; offset < totalItemCount; offset += batchSize) {
            List<Object[]> batch = new ArrayList<>(batchSize);
            int end = Math.min(offset + batchSize, totalItemCount);
            for (int i = offset; i < end; i++) {
                long id = i + 1L;
                long orderId = (i / orderItemPerOrder) + 1L;
                long productId = (id % 100_000L) + 1;
                long categoryId = (id % 155L) + 1;
                int quantity = (int) (id % 5) + 1;
                long unitPrice = ((id % 5000) + 1) * 100L;
                batch.add(new Object[]{id, orderId, productId, categoryId, quantity, unitPrice});
            }
            jdbcTemplate.batchUpdate(itemSql, batch);
            status.set("order items 적재 중: " + end + "/" + totalItemCount);
        }

        status.set("완료");
    }
}
