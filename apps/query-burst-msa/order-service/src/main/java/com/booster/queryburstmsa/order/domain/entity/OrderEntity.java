package com.booster.queryburstmsa.order.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburstmsa.order.domain.OrderStatus;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "customer_order",
        indexes = {
                @Index(name = "idx_customer_order_member_id", columnList = "member_id"),
                @Index(name = "idx_customer_order_status", columnList = "status"),
                @Index(name = "idx_customer_order_ordered_at", columnList = "ordered_at"),
                @Index(name = "idx_customer_order_idempotency_key", columnList = "idempotency_key")
        }
)
public class OrderEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    public static OrderEntity create(
            Long orderId,
            Long memberId,
            String reservationId,
            String idempotencyKey,
            OrderStatus status,
            long totalAmount
    ) {
        OrderEntity entity = new OrderEntity();
        entity.id = orderId;
        entity.memberId = memberId;
        entity.reservationId = reservationId;
        entity.idempotencyKey = idempotencyKey;
        entity.status = status;
        entity.totalAmount = totalAmount;
        entity.orderedAt = LocalDateTime.now();
        return entity;
    }

    public void addItem(Long productId, Long categoryId, int quantity, long unitPrice) {
        items.add(OrderItemEntity.create(this, productId, categoryId, quantity, unitPrice));
    }

    public void pay() {
        if (status == OrderStatus.STOCK_RESERVED) {
            status = OrderStatus.PAID;
        }
    }

    public void ship() {
        if (status == OrderStatus.PAID) {
            status = OrderStatus.SHIPPED;
        }
    }

    public void deliver() {
        if (status == OrderStatus.SHIPPED) {
            status = OrderStatus.DELIVERED;
        }
    }

    public void cancel() {
        status = OrderStatus.CANCELED;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public List<OrderItemEntity> getItems() {
        return items;
    }
}
