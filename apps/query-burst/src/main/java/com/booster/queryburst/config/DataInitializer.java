package com.booster.queryburst.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 쿼리 실습용 대용량 더미 데이터 적재기
 *
 * 활성화 조건: dev 프로파일
 * 실행 방법: POST /api/data-init (직접 호출 방식)
 *
 * 적재 순서 (FK 의존성):
 *   category → member → product → orders → order_item
 *
 * 병렬 전략:
 *   - category: 소량(~155건)이므로 단건 순차 적재
 *   - 나머지: batchSize 단위로 청크 분할 → Fixed Thread Pool로 병렬 배치 INSERT
 *
 * 성능 핵심:
 *   - reWriteBatchedInserts=true (JDBC URL 옵션): N개 단건 INSERT → 단일 multi-row INSERT 변환
 *   - ThreadLocalRandom: 스레드별 독립 난수 생성 (CAS 경합 없음)
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer {

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private final AtomicReference<String> status = new AtomicReference<>("대기 중");

    private final JdbcTemplate jdbcTemplate;

    @Value("${data.init.member-count:10000000}")
    private long memberCount;

    @Value("${data.init.product-count:1000000}")
    private long productCount;

    @Value("${data.init.order-count:30000000}")
    private long orderCount;

    @Value("${data.init.order-item-per-order:3}")
    private int orderItemPerOrder;

    @Value("${data.init.batch-size:5000}")
    private int batchSize;

    @Value("${data.init.thread-count:8}")
    private int threadCount;

    // 등급별 가중치 배열: BRONZE(60%) SILVER(25%) GOLD(12%) VIP(3%)
    private static final String[] GRADES = {
        "BRONZE","BRONZE","BRONZE","BRONZE","BRONZE","BRONZE","BRONZE","BRONZE","BRONZE","BRONZE","BRONZE","BRONZE",
        "SILVER","SILVER","SILVER","SILVER","SILVER",
        "GOLD","GOLD",
        "VIP"
    };

    // 주문 상태 가중치: DELIVERED(50%) SHIPPED(20%) PAID(15%) CANCELED(10%) PENDING(5%)
    private static final String[] ORDER_STATUSES = {
        "DELIVERED","DELIVERED","DELIVERED","DELIVERED","DELIVERED","DELIVERED","DELIVERED","DELIVERED","DELIVERED","DELIVERED",
        "SHIPPED","SHIPPED","SHIPPED","SHIPPED",
        "PAID","PAID","PAID",
        "CANCELED","CANCELED",
        "PENDING"
    };

    // 상품 상태 가중치: ACTIVE(70%) INACTIVE(20%) SOLD_OUT(10%)
    private static final String[] PRODUCT_STATUSES = {
        "ACTIVE","ACTIVE","ACTIVE","ACTIVE","ACTIVE","ACTIVE","ACTIVE",
        "INACTIVE","INACTIVE",
        "SOLD_OUT"
    };

    private static final String[] REGIONS = {
        "서울", "서울", "서울",   // 서울 30%
        "경기", "경기",           // 경기 20%
        "부산", "인천", "대구",   // 광역시 30%
        "광주", "대전", "울산",
        "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"
    };

    // insertCategories() 에서 채워지는 depth-3 카테고리 ID 목록 (Product FK용)
    private long[] depth3CategoryIds;

    // ─── SQL ────────────────────────────────────────────────────────────────

    private static final String MEMBER_SQL =
        "INSERT INTO member (id, email, name, grade, region, created_at, updated_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";

    private static final String PRODUCT_SQL =
        "INSERT INTO product (id, name, price, stock, status, category_id, seller_id, last_fence_token, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";

    private static final String ORDER_SQL =
        "INSERT INTO orders (id, member_id, status, total_amount, ordered_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";

    private static final String ORDER_ITEM_SQL =
        "INSERT INTO order_item (id, order_id, product_id, quantity, unit_price, created_at, updated_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";

    // ─── 진입점 ──────────────────────────────────────────────────────────────

    /** Controller에서 비동기로 호출. running 플래그로 중복 실행 방지 */
    public void initializeAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 적재가 진행 중입니다.");
        }
        CompletableFuture.runAsync(() -> {
            try {
                initialize();
            } catch (Exception e) {
                String errorMsg = "에러 발생: " + e.getMessage();
                status.set(errorMsg);
                log.error("데이터 적재 중 에러 발생", e);
            } finally {
                running.set(false);
            }
        });
    }

    private void initialize() {
        long totalStart = System.currentTimeMillis();
        log.info("========== 더미 데이터 적재 시작 ==========");

        insertCategories();
        insertInParallel("member",     MEMBER_SQL,     memberCount,                    this::memberBatch);
        insertInParallel("product",    PRODUCT_SQL,    productCount,                   this::productBatch);
        insertInParallel("orders",     ORDER_SQL,      orderCount,                     this::orderBatch);
        insertInParallel("order_item", ORDER_ITEM_SQL, orderCount * orderItemPerOrder, this::orderItemBatch);

        status.set("완료 (총 " + (System.currentTimeMillis() - totalStart) / 1000 + "초)");
        log.info("========== 더미 데이터 적재 완료: {} ==========", status.get());
    }

    // ─── Category (순차, 계층 구조) ──────────────────────────────────────────

    private void insertCategories() {
        log.info("[category] 적재 시작");
        long start = System.currentTimeMillis();

        String[] depth1Names = {"전자기기", "의류", "식품", "도서", "스포츠"};
        String[][] depth2Names = {
            {"스마트폰", "노트북", "태블릿", "TV", "카메라"},
            {"남성의류", "여성의류", "아우터", "신발", "가방"},
            {"과자/스낵", "음료", "신선식품", "건강식품", "간편식"},
            {"소설", "자기계발", "IT/컴퓨터", "만화", "어린이도서"},
            {"헬스", "등산", "수영", "구기종목", "사이클"}
        };

        String sql = "INSERT INTO category (id, name, parent_id, depth, created_at, updated_at) VALUES (?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        LocalDateTime now = LocalDateTime.now();
        long id = 1;
        List<Long> depth3Ids = new ArrayList<>();

        // depth 1
        long[] depth1Ids = new long[depth1Names.length];
        for (int i = 0; i < depth1Names.length; i++) {
            jdbcTemplate.update(sql, id, depth1Names[i], null, 1, now, now);
            depth1Ids[i] = id++;
        }

        // depth 2, 3
        for (int i = 0; i < depth1Names.length; i++) {
            for (int j = 0; j < depth2Names[i].length; j++) {
                long depth2Id = id++;
                jdbcTemplate.update(sql, depth2Id, depth2Names[i][j], depth1Ids[i], 2, now, now);

                for (int k = 1; k <= 3; k++) {
                    long depth3Id = id++;
                    jdbcTemplate.update(sql, depth3Id, depth2Names[i][j] + " > " + k + "단계", depth2Id, 3, now, now);
                    depth3Ids.add(depth3Id);
                }
            }
        }

        depth3CategoryIds = depth3Ids.stream().mapToLong(Long::longValue).toArray();

        log.info("[category] 완료: {}건 ({}ms)", id - 1, System.currentTimeMillis() - start);
    }

    // ─── 공통 병렬 배치 적재 ─────────────────────────────────────────────────

    @FunctionalInterface
    interface BatchSupplier {
        List<Object[]> generate(long startId, long endId);
    }

    private void insertInParallel(String table, String sql, long total, BatchSupplier supplier) {
        status.set("[" + table + "] 적재 중...");
        log.info("[{}] 적재 시작: {}건", table, String.format("%,d", total));
        long start = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong counter = new AtomicLong(0);
        long logInterval = Math.max(total / 10, batchSize);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (long offset = 0; offset < total; offset += batchSize) {
            long batchStart = offset + 1;
            long batchEnd = Math.min(offset + batchSize, total);

            futures.add(CompletableFuture.runAsync(() -> {
                List<Object[]> batch = supplier.generate(batchStart, batchEnd);
                jdbcTemplate.batchUpdate(sql, batch);

                long prev = counter.get();
                long done = counter.addAndGet(batch.size());
                // 10% 경계를 넘는 순간만 로깅
                if (done / logInterval != prev / logInterval) {
                    log.info("[{}] {}% ({}/{})",
                        table,
                        done * 100 / total,
                        String.format("%,d", done),
                        String.format("%,d", total));
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        log.info("[{}] 완료: {}건 ({}초)", table,
            String.format("%,d", total),
            (System.currentTimeMillis() - start) / 1000);
    }

    // ─── Batch 생성기 ────────────────────────────────────────────────────────

    private List<Object[]> memberBatch(long startId, long endId) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batch = new ArrayList<>((int) (endId - startId + 1));

        for (long id = startId; id <= endId; id++) {
            batch.add(new Object[]{
                id,
                "user" + id + "@example.com",
                "회원" + id,
                GRADES[rnd.nextInt(GRADES.length)],
                REGIONS[rnd.nextInt(REGIONS.length)],
                now, now
            });
        }
        return batch;
    }

    private List<Object[]> productBatch(long startId, long endId) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batch = new ArrayList<>((int) (endId - startId + 1));

        for (long id = startId; id <= endId; id++) {
            long price = (rnd.nextLong(1, 5001) * 100); // 100 ~ 500,000 (백원 단위)
            batch.add(new Object[]{
                id,
                "상품_" + id,
                price,
                rnd.nextInt(0, 1001),
                PRODUCT_STATUSES[rnd.nextInt(PRODUCT_STATUSES.length)],
                depth3CategoryIds[rnd.nextInt(depth3CategoryIds.length)],
                rnd.nextLong(1, memberCount + 1),
                now, now
            });
        }
        return batch;
    }

    private List<Object[]> orderBatch(long startId, long endId) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        long minEpoch = now.minusYears(2).toEpochSecond(ZoneOffset.UTC);
        long maxEpoch = now.toEpochSecond(ZoneOffset.UTC);
        List<Object[]> batch = new ArrayList<>((int) (endId - startId + 1));

        for (long id = startId; id <= endId; id++) {
            LocalDateTime orderedAt = LocalDateTime.ofEpochSecond(
                rnd.nextLong(minEpoch, maxEpoch), 0, ZoneOffset.UTC);
            batch.add(new Object[]{
                id,
                rnd.nextLong(1, memberCount + 1),
                ORDER_STATUSES[rnd.nextInt(ORDER_STATUSES.length)],
                rnd.nextLong(1000, 2_000_001),
                orderedAt,
                orderedAt, orderedAt
            });
        }
        return batch;
    }

    private List<Object[]> orderItemBatch(long startId, long endId) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batch = new ArrayList<>((int) (endId - startId + 1));

        for (long id = startId; id <= endId; id++) {
            long unitPrice = rnd.nextLong(1, 5001) * 100;
            batch.add(new Object[]{
                id,
                rnd.nextLong(1, orderCount + 1),
                rnd.nextLong(1, productCount + 1),
                rnd.nextInt(1, 6),
                unitPrice,
                now, now
            });
        }
        return batch;
    }
}
