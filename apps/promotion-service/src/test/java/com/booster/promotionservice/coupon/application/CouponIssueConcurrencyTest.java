package com.booster.promotionservice.coupon.application;

import com.booster.promotionservice.coupon.application.dto.IssueCouponCommand;
import com.booster.promotionservice.coupon.domain.*;
import com.booster.promotionservice.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("쿠폰 발급 동시성 테스트")
class CouponIssueConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private CouponPolicyRepository couponPolicyRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private CouponPolicy testPolicy;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        testPolicy = CouponPolicy.create(
                "동시성 테스트 쿠폰",
                "동시성 테스트용",
                DiscountType.FIXED,
                5000,
                10000, // DB 수량은 충분히
                now.minusDays(1),
                now.plusDays(7),
                now.plusDays(30)
        );
        testPolicy.activate();
        couponPolicyRepository.save(testPolicy);
    }

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAll();
        couponPolicyRepository.deleteAll();

        Set<String> keys = redisTemplate.keys("coupon:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("100명이 동시에 100개 쿠폰 요청 시 정확히 100개만 발급된다")
    void concurrency_100users_100coupons() throws InterruptedException {
        // given
        int couponQuantity = 100;
        int threadCount = 100;

        couponIssueService.initializeCouponStock(testPolicy.getId(), couponQuantity);

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponIssueService.issue(IssueCouponCommand.of(testPolicy.getId(), userId));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        long issuedCount = issuedCouponRepository.count();
        int remainingStock = couponIssueService.getRemainingStock(testPolicy.getId());

        assertThat(issuedCount).isEqualTo(couponQuantity);
        assertThat(successCount.get()).isEqualTo(couponQuantity);
        assertThat(remainingStock).isEqualTo(0);

        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("발급 실패: " + failCount.get());
        System.out.println("DB 발급 수: " + issuedCount);
        System.out.println("Redis 잔여 재고: " + remainingStock);
    }

    @Test
    @DisplayName("1000명이 동시에 100개 쿠폰 요청 시 정확히 100개만 발급된다 (재고 초과 방지)")
    void concurrency_1000users_100coupons_noOverselling() throws InterruptedException {
        // given
        int couponQuantity = 100;
        int threadCount = 1000;

        couponIssueService.initializeCouponStock(testPolicy.getId(), couponQuantity);

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponIssueService.issue(IssueCouponCommand.of(testPolicy.getId(), userId));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage().contains("소진")) {
                        soldOutCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        long issuedCount = issuedCouponRepository.count();
        int remainingStock = couponIssueService.getRemainingStock(testPolicy.getId());

        // 핵심: 정확히 100개만 발급되어야 함
        assertThat(issuedCount).isEqualTo(couponQuantity);
        assertThat(successCount.get()).isEqualTo(couponQuantity);
        assertThat(remainingStock).isEqualTo(0);
        assertThat(soldOutCount.get()).isEqualTo(threadCount - couponQuantity);

        System.out.println("=== 동시성 테스트 결과 (재고 초과 방지) ===");
        System.out.println("요청 수: " + threadCount);
        System.out.println("쿠폰 수량: " + couponQuantity);
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("재고 소진: " + soldOutCount.get());
        System.out.println("DB 발급 수: " + issuedCount);
    }

    @Test
    @DisplayName("같은 사용자가 여러 번 요청해도 1번만 발급된다 (중복 방지)")
    void concurrency_sameUser_noDuplicate() throws InterruptedException {
        // given
        int couponQuantity = 100;
        int requestCount = 10; // 같은 사용자가 10번 요청
        long userId = 1L;

        couponIssueService.initializeCouponStock(testPolicy.getId(), couponQuantity);

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                try {
                    couponIssueService.issue(IssueCouponCommand.of(testPolicy.getId(), userId));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage().contains("이미 발급")) {
                        duplicateCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        boolean exists = issuedCouponRepository.existsByUserIdAndCouponPolicyId(userId, testPolicy.getId());

        // 핵심: 같은 사용자에게 1번만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(requestCount - 1);
        assertThat(issuedCouponRepository.existsByUserIdAndCouponPolicyId(userId, testPolicy.getId())).isTrue();

        System.out.println("=== 동시성 테스트 결과 (중복 방지) ===");
        System.out.println("같은 사용자 요청 수: " + requestCount);
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("중복 차단: " + duplicateCount.get());
    }
}
