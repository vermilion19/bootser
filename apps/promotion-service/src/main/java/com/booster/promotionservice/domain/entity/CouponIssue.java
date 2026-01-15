package com.booster.promotionservice.domain.entity;

import com.booster.common.SnowflakeGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "coupon_issue",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_coupon_user",
                        columnNames = {"coupon_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id")
        }
)
public class CouponIssue {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    public CouponIssue(Long couponId, Long userId) {
        this.id = SnowflakeGenerator.nextId();
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }

    public void use() {
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }
}
