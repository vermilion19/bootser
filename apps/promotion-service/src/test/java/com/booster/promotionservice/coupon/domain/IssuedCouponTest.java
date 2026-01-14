package com.booster.promotionservice.coupon.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IssuedCoupon 도메인 테스트")
class IssuedCouponTest {

    @Nested
    @DisplayName("발급 쿠폰 생성")
    class Create {

        @Test
        @DisplayName("발급 쿠폰을 생성하면 UNUSED 상태이다")
        void createIssuedCoupon_success() {
            // given
            Long policyId = 1L;
            Long userId = 100L;
            LocalDateTime expireAt = LocalDateTime.now().plusDays(30);

            // when
            IssuedCoupon coupon = IssuedCoupon.create(policyId, userId, expireAt);

            // then
            assertThat(coupon.getId()).isNotNull();
            assertThat(coupon.getCouponPolicyId()).isEqualTo(policyId);
            assertThat(coupon.getUserId()).isEqualTo(userId);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.UNUSED);
            assertThat(coupon.getIssuedAt()).isNotNull();
            assertThat(coupon.getUsedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("쿠폰 사용")
    class Use {

        @Test
        @DisplayName("UNUSED 상태의 쿠폰을 사용하면 USED 상태가 된다")
        void use_success() {
            // given
            IssuedCoupon coupon = createDefaultCoupon();

            // when
            coupon.use();

            // then
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(coupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 사용된 쿠폰을 사용하면 예외가 발생한다")
        void use_alreadyUsed_fail() {
            // given
            IssuedCoupon coupon = createDefaultCoupon();
            coupon.use();

            // when & then
            assertThatThrownBy(coupon::use)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 사용된 쿠폰입니다.");
        }

        @Test
        @DisplayName("만료된 쿠폰을 사용하면 예외가 발생한다")
        void use_expired_fail() {
            // given
            IssuedCoupon coupon = IssuedCoupon.create(
                    1L, 100L, LocalDateTime.now().minusDays(1) // 이미 만료
            );

            // when & then
            assertThatThrownBy(coupon::use)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("만료된 쿠폰입니다.");
        }
    }

    @Nested
    @DisplayName("쿠폰 만료 처리")
    class Expire {

        @Test
        @DisplayName("UNUSED 상태의 쿠폰을 만료 처리할 수 있다")
        void expire_success() {
            // given
            IssuedCoupon coupon = createDefaultCoupon();

            // when
            coupon.expire();

            // then
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        }

        @Test
        @DisplayName("이미 사용된 쿠폰은 만료 처리할 수 없다")
        void expire_alreadyUsed_fail() {
            // given
            IssuedCoupon coupon = createDefaultCoupon();
            coupon.use();

            // when & then
            assertThatThrownBy(coupon::expire)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("미사용 쿠폰만 만료 처리할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("사용 가능 여부 확인")
    class IsUsable {

        @Test
        @DisplayName("UNUSED 상태이고 만료되지 않았으면 사용 가능하다")
        void isUsable_true() {
            // given
            IssuedCoupon coupon = createDefaultCoupon();

            // when & then
            assertThat(coupon.isUsable()).isTrue();
        }

        @Test
        @DisplayName("USED 상태이면 사용 불가능하다")
        void isUsable_used_false() {
            // given
            IssuedCoupon coupon = createDefaultCoupon();
            coupon.use();

            // when & then
            assertThat(coupon.isUsable()).isFalse();
        }

        @Test
        @DisplayName("만료되었으면 사용 불가능하다")
        void isUsable_expired_false() {
            // given
            IssuedCoupon coupon = IssuedCoupon.create(
                    1L, 100L, LocalDateTime.now().minusDays(1)
            );

            // when & then
            assertThat(coupon.isUsable()).isFalse();
        }
    }

    private IssuedCoupon createDefaultCoupon() {
        return IssuedCoupon.create(1L, 100L, LocalDateTime.now().plusDays(30));
    }
}
