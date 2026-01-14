package com.booster.promotionservice.coupon.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CouponPolicy 도메인 테스트")
class CouponPolicyTest {

    @Nested
    @DisplayName("쿠폰 정책 생성")
    class Create {

        @Test
        @DisplayName("정상적인 파라미터로 쿠폰 정책을 생성한다")
        void createCouponPolicy_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(7);
            LocalDateTime expireAt = now.plusDays(30);

            // when
            CouponPolicy policy = CouponPolicy.create(
                    "신규 가입 쿠폰",
                    "신규 가입 시 5000원 할인",
                    DiscountType.FIXED,
                    5000,
                    10000,
                    startAt,
                    endAt,
                    expireAt
            );

            // then
            assertThat(policy.getId()).isNotNull();
            assertThat(policy.getName()).isEqualTo("신규 가입 쿠폰");
            assertThat(policy.getDiscountType()).isEqualTo(DiscountType.FIXED);
            assertThat(policy.getDiscountValue()).isEqualTo(5000);
            assertThat(policy.getTotalQuantity()).isEqualTo(10000);
            assertThat(policy.getIssuedQuantity()).isEqualTo(0);
            assertThat(policy.getStatus()).isEqualTo(PolicyStatus.PENDING);
        }

        @Test
        @DisplayName("할인율이 100%를 초과하면 예외가 발생한다")
        void createCouponPolicy_percentageOver100_fail() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> CouponPolicy.create(
                    "테스트 쿠폰",
                    "설명",
                    DiscountType.PERCENTAGE,
                    150, // 150% - 잘못된 값
                    100,
                    now.plusDays(1),
                    now.plusDays(7),
                    now.plusDays(30)
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("할인율은 100%를 초과할 수 없습니다.");
        }

        @Test
        @DisplayName("할인 값이 0이하면 예외가 발생한다")
        void createCouponPolicy_invalidDiscountValue_fail() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> CouponPolicy.create(
                    "테스트 쿠폰",
                    "설명",
                    DiscountType.FIXED,
                    0, // 0 - 잘못된 값
                    100,
                    now.plusDays(1),
                    now.plusDays(7),
                    now.plusDays(30)
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("할인 값은 0보다 커야 합니다.");
        }

        @Test
        @DisplayName("수량이 0이하면 예외가 발생한다")
        void createCouponPolicy_invalidQuantity_fail() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> CouponPolicy.create(
                    "테스트 쿠폰",
                    "설명",
                    DiscountType.FIXED,
                    5000,
                    0, // 0 - 잘못된 값
                    now.plusDays(1),
                    now.plusDays(7),
                    now.plusDays(30)
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("발급 수량은 0보다 커야 합니다.");
        }

        @Test
        @DisplayName("시작 시간이 종료 시간보다 늦으면 예외가 발생한다")
        void createCouponPolicy_invalidPeriod_fail() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> CouponPolicy.create(
                    "테스트 쿠폰",
                    "설명",
                    DiscountType.FIXED,
                    5000,
                    100,
                    now.plusDays(7), // 시작이 종료보다 늦음
                    now.plusDays(1),
                    now.plusDays(30)
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("시작 시간은 종료 시간보다 이전이어야 합니다.");
        }
    }

    @Nested
    @DisplayName("쿠폰 정책 상태 변경")
    class StatusChange {

        @Test
        @DisplayName("PENDING 상태의 정책을 활성화할 수 있다")
        void activate_success() {
            // given
            CouponPolicy policy = createDefaultPolicy();

            // when
            policy.activate();

            // then
            assertThat(policy.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 활성화하면 예외가 발생한다")
        void activate_notPending_fail() {
            // given
            CouponPolicy policy = createDefaultPolicy();
            policy.activate(); // 이미 ACTIVE

            // when & then
            assertThatThrownBy(policy::activate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("대기 상태의 정책만 활성화할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("발급 수량 증가")
    class IncrementIssuedQuantity {

        @Test
        @DisplayName("발급 수량을 증가시킬 수 있다")
        void incrementIssuedQuantity_success() {
            // given
            CouponPolicy policy = createDefaultPolicy();

            // when
            policy.incrementIssuedQuantity();

            // then
            assertThat(policy.getIssuedQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("총 수량만큼 발급하면 EXHAUSTED 상태가 된다")
        void incrementIssuedQuantity_exhausted() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CouponPolicy policy = CouponPolicy.create(
                    "테스트 쿠폰", "설명", DiscountType.FIXED, 5000, 2,
                    now.plusDays(1), now.plusDays(7), now.plusDays(30)
            );
            policy.activate();

            // when
            policy.incrementIssuedQuantity();
            policy.incrementIssuedQuantity();

            // then
            assertThat(policy.getIssuedQuantity()).isEqualTo(2);
            assertThat(policy.getStatus()).isEqualTo(PolicyStatus.EXHAUSTED);
        }

        @Test
        @DisplayName("총 수량을 초과하여 발급하면 예외가 발생한다")
        void incrementIssuedQuantity_overQuantity_fail() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CouponPolicy policy = CouponPolicy.create(
                    "테스트 쿠폰", "설명", DiscountType.FIXED, 5000, 1,
                    now.plusDays(1), now.plusDays(7), now.plusDays(30)
            );
            policy.incrementIssuedQuantity(); // 1개 발급 완료

            // when & then
            assertThatThrownBy(policy::incrementIssuedQuantity)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("발급 가능 수량을 초과했습니다.");
        }
    }

    @Nested
    @DisplayName("발급 가능 여부 확인")
    class IsIssuable {

        @Test
        @DisplayName("ACTIVE 상태이고 기간 내이고 재고가 있으면 발급 가능하다")
        void isIssuable_true() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CouponPolicy policy = CouponPolicy.create(
                    "테스트 쿠폰", "설명", DiscountType.FIXED, 5000, 100,
                    now.minusDays(1), now.plusDays(7), now.plusDays(30)
            );
            policy.activate();

            // when & then
            assertThat(policy.isIssuable()).isTrue();
        }

        @Test
        @DisplayName("PENDING 상태이면 발급 불가능하다")
        void isIssuable_pending_false() {
            // given
            CouponPolicy policy = createDefaultPolicy();

            // when & then
            assertThat(policy.isIssuable()).isFalse();
        }

        @Test
        @DisplayName("재고가 소진되면 발급 불가능하다")
        void isIssuable_exhausted_false() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CouponPolicy policy = CouponPolicy.create(
                    "테스트 쿠폰", "설명", DiscountType.FIXED, 5000, 1,
                    now.minusDays(1), now.plusDays(7), now.plusDays(30)
            );
            policy.activate();
            policy.incrementIssuedQuantity();

            // when & then
            assertThat(policy.isIssuable()).isFalse();
        }
    }

    private CouponPolicy createDefaultPolicy() {
        LocalDateTime now = LocalDateTime.now();
        return CouponPolicy.create(
                "테스트 쿠폰",
                "테스트용 쿠폰입니다",
                DiscountType.FIXED,
                5000,
                100,
                now.plusDays(1),
                now.plusDays(7),
                now.plusDays(30)
        );
    }
}
