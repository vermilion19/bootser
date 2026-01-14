package com.booster.promotionservice.coupon.application;

import com.booster.promotionservice.coupon.application.dto.IssueCouponCommand;
import com.booster.promotionservice.coupon.domain.*;
import com.booster.promotionservice.coupon.exception.AlreadyIssuedCouponException;
import com.booster.promotionservice.coupon.exception.CouponPolicyNotFoundException;
import com.booster.promotionservice.coupon.exception.CouponSoldOutException;
import com.booster.promotionservice.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CouponIssueService 통합 테스트")
class CouponIssueServiceTest extends IntegrationTestSupport {

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
        // 테스트용 쿠폰 정책 생성
        LocalDateTime now = LocalDateTime.now();
        testPolicy = CouponPolicy.create(
                "테스트 쿠폰",
                "테스트용 쿠폰입니다",
                DiscountType.FIXED,
                5000,
                100,
                now.minusDays(1),
                now.plusDays(7),
                now.plusDays(30)
        );
        testPolicy.activate();
        couponPolicyRepository.save(testPolicy);

        // Redis 재고 초기화
        couponIssueService.initializeCouponStock(testPolicy.getId(), 100);
    }

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAll();
        couponPolicyRepository.deleteAll();

        // Redis 키 정리
        Set<String> keys = redisTemplate.keys("coupon:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Nested
    @DisplayName("쿠폰 발급")
    class Issue {

        @Test
        @DisplayName("정상적으로 쿠폰을 발급한다")
        void issue_success() {
            // given
            Long userId = 1L;
            IssueCouponCommand command = IssueCouponCommand.of(testPolicy.getId(), userId);

            // when
            IssuedCoupon issuedCoupon = couponIssueService.issue(command);

            // then
            assertThat(issuedCoupon).isNotNull();
            assertThat(issuedCoupon.getCouponPolicyId()).isEqualTo(testPolicy.getId());
            assertThat(issuedCoupon.getUserId()).isEqualTo(userId);
            assertThat(issuedCoupon.getStatus()).isEqualTo(CouponStatus.UNUSED);

            // Redis 재고 확인
            int remainingStock = couponIssueService.getRemainingStock(testPolicy.getId());
            assertThat(remainingStock).isEqualTo(99);

            // Redis 발급 여부 확인
            boolean hasIssued = couponIssueService.hasAlreadyIssued(testPolicy.getId(), userId);
            assertThat(hasIssued).isTrue();
        }

        @Test
        @DisplayName("동일 사용자가 중복 발급 요청 시 예외가 발생한다")
        void issue_duplicateUser_fail() {
            // given
            Long userId = 1L;
            IssueCouponCommand command = IssueCouponCommand.of(testPolicy.getId(), userId);
            couponIssueService.issue(command); // 첫 번째 발급

            // when & then
            assertThatThrownBy(() -> couponIssueService.issue(command))
                    .isInstanceOf(AlreadyIssuedCouponException.class);
        }

        @Test
        @DisplayName("재고가 소진되면 예외가 발생한다")
        void issue_soldOut_fail() {
            // given
            // 재고를 1개만 설정
            couponIssueService.initializeCouponStock(testPolicy.getId(), 1);

            // 첫 번째 발급
            couponIssueService.issue(IssueCouponCommand.of(testPolicy.getId(), 1L));

            // when & then
            assertThatThrownBy(() ->
                    couponIssueService.issue(IssueCouponCommand.of(testPolicy.getId(), 2L))
            ).isInstanceOf(CouponSoldOutException.class);
        }

        @Test
        @DisplayName("존재하지 않는 정책 ID로 발급 시 예외가 발생한다")
        void issue_policyNotFound_fail() {
            // given
            Long invalidPolicyId = 999999L;
            couponIssueService.initializeCouponStock(invalidPolicyId, 100);

            // Redis에 재고가 있어도 DB에 정책이 없으면 실패
            // 먼저 Redis에서는 성공하지만 DB 조회에서 실패함
            IssueCouponCommand command = IssueCouponCommand.of(invalidPolicyId, 1L);

            // when & then
            assertThatThrownBy(() -> couponIssueService.issue(command))
                    .isInstanceOf(CouponPolicyNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("재고 관련")
    class Stock {

        @Test
        @DisplayName("재고를 초기화하고 조회할 수 있다")
        void initializeAndGetStock() {
            // given
            Long policyId = testPolicy.getId();
            int quantity = 500;

            // when
            couponIssueService.initializeCouponStock(policyId, quantity);
            int stock = couponIssueService.getRemainingStock(policyId);

            // then
            assertThat(stock).isEqualTo(quantity);
        }

        @Test
        @DisplayName("존재하지 않는 정책의 재고는 0이다")
        void getRemainingStock_notExists_returnsZero() {
            // given
            Long invalidPolicyId = 999999L;

            // when
            int stock = couponIssueService.getRemainingStock(invalidPolicyId);

            // then
            assertThat(stock).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("발급 여부 확인")
    class HasAlreadyIssued {

        @Test
        @DisplayName("발급받지 않은 사용자는 false를 반환한다")
        void hasAlreadyIssued_notIssued_false() {
            // given
            Long userId = 999L;

            // when
            boolean result = couponIssueService.hasAlreadyIssued(testPolicy.getId(), userId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("발급받은 사용자는 true를 반환한다")
        void hasAlreadyIssued_issued_true() {
            // given
            Long userId = 1L;
            couponIssueService.issue(IssueCouponCommand.of(testPolicy.getId(), userId));

            // when
            boolean result = couponIssueService.hasAlreadyIssued(testPolicy.getId(), userId);

            // then
            assertThat(result).isTrue();
        }
    }
}
