package com.booster.promotionservice.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    @Id
    private Long id; // Auto Increment 아님!

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer issuedQuantity = 0; // 통계 목적 (Redis와 별개)

    @Column(nullable = false)
    private Integer discountAmount;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    // 생성자에서 ID 주입
    public Coupon(String title, Integer totalQuantity, Integer discountAmount, LocalDateTime startAt, LocalDateTime endAt) {
        this.id = SnowflakeGenerator.nextId(); // Snowflake ID 생성
        this.title = title;
        this.totalQuantity = totalQuantity;
        this.discountAmount = discountAmount;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    // 재고 증가 메서드 (동시성 처리 주의 필요하지만, 여기선 단순 통계용 업데이트)
    public void increaseIssuedQuantity() {
        this.issuedQuantity++;
    }

}
