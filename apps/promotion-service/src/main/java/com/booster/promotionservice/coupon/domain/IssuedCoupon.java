package com.booster.promotionservice.coupon.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "issued_coupon",
        indexes = {
                @Index(name = "idx_issued_coupon_user_policy", columnList = "userId, couponPolicyId"),
                @Index(name = "idx_issued_coupon_policy", columnList = "couponPolicyId")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_policy", columnNames = {"userId", "couponPolicyId"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long couponPolicyId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    @Column(nullable = false)
    private LocalDateTime expireAt;

    private IssuedCoupon(Long couponPolicyId, Long userId, LocalDateTime expireAt) {
        this.id = SnowflakeGenerator.nextId();
        this.couponPolicyId = couponPolicyId;
        this.userId = userId;
        this.status = CouponStatus.UNUSED;
        this.issuedAt = LocalDateTime.now();
        this.expireAt = expireAt;
    }

    public static IssuedCoupon create(Long couponPolicyId, Long userId, LocalDateTime expireAt) {
        return new IssuedCoupon(couponPolicyId, userId, expireAt);
    }

    public void use() {
        validateUsable();
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void expire() {
        if (this.status != CouponStatus.UNUSED) {
            throw new IllegalStateException("미사용 쿠폰만 만료 처리할 수 있습니다.");
        }
        this.status = CouponStatus.EXPIRED;
    }

    public boolean isUsable() {
        return this.status == CouponStatus.UNUSED
                && LocalDateTime.now().isBefore(this.expireAt);
    }

    private void validateUsable() {
        if (this.status == CouponStatus.USED) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }
        if (this.status == CouponStatus.EXPIRED || LocalDateTime.now().isAfter(this.expireAt)) {
            throw new IllegalStateException("만료된 쿠폰입니다.");
        }
    }
}
