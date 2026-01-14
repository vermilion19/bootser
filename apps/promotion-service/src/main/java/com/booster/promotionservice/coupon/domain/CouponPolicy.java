package com.booster.promotionservice.coupon.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponPolicy extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false)
    private Integer discountValue;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer issuedQuantity;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private LocalDateTime expireAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    private CouponPolicy(String name, String description, DiscountType discountType,
                         Integer discountValue, Integer totalQuantity,
                         LocalDateTime startAt, LocalDateTime endAt, LocalDateTime expireAt) {
        this.id = SnowflakeGenerator.nextId();
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.startAt = startAt;
        this.endAt = endAt;
        this.expireAt = expireAt;
        this.status = PolicyStatus.PENDING;
    }

    public static CouponPolicy create(String name, String description, DiscountType discountType,
                                      Integer discountValue, Integer totalQuantity,
                                      LocalDateTime startAt, LocalDateTime endAt, LocalDateTime expireAt) {
        validateDiscountValue(discountType, discountValue);
        validateQuantity(totalQuantity);
        validatePeriod(startAt, endAt, expireAt);

        return new CouponPolicy(name, description, discountType, discountValue,
                totalQuantity, startAt, endAt, expireAt);
    }

    public void activate() {
        if (this.status != PolicyStatus.PENDING) {
            throw new IllegalStateException("대기 상태의 정책만 활성화할 수 있습니다.");
        }
        this.status = PolicyStatus.ACTIVE;
    }

    public void incrementIssuedQuantity() {
        if (this.issuedQuantity >= this.totalQuantity) {
            throw new IllegalStateException("발급 가능 수량을 초과했습니다.");
        }
        this.issuedQuantity++;

        if (this.issuedQuantity.equals(this.totalQuantity)) {
            this.status = PolicyStatus.EXHAUSTED;
        }
    }

    public boolean isIssuable() {
        LocalDateTime now = LocalDateTime.now();
        return this.status == PolicyStatus.ACTIVE
                && now.isAfter(startAt)
                && now.isBefore(endAt)
                && this.issuedQuantity < this.totalQuantity;
    }

    public int getRemainingQuantity() {
        return this.totalQuantity - this.issuedQuantity;
    }

    private static void validateDiscountValue(DiscountType type, Integer value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("할인 값은 0보다 커야 합니다.");
        }
        if (type == DiscountType.PERCENTAGE && value > 100) {
            throw new IllegalArgumentException("할인율은 100%를 초과할 수 없습니다.");
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("발급 수량은 0보다 커야 합니다.");
        }
    }

    private static void validatePeriod(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime expireAt) {
        if (startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 이전이어야 합니다.");
        }
        if (endAt.isAfter(expireAt)) {
            throw new IllegalArgumentException("발급 종료 시간은 쿠폰 만료 시간보다 이전이어야 합니다.");
        }
    }
}
