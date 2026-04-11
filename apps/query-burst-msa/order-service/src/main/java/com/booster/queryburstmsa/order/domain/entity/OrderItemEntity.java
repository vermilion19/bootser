package com.booster.queryburstmsa.order.domain.entity;

import com.booster.common.SnowflakeGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "customer_order_item",
        indexes = {
                @Index(name = "idx_customer_order_item_order_id", columnList = "order_id"),
                @Index(name = "idx_customer_order_item_product_id", columnList = "product_id")
        }
)
public class OrderItemEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    protected OrderItemEntity() {
    }

    public static OrderItemEntity create(OrderEntity order, Long productId, Long categoryId, int quantity, long unitPrice) {
        OrderItemEntity entity = new OrderItemEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.order = order;
        entity.productId = productId;
        entity.categoryId = categoryId;
        entity.quantity = quantity;
        entity.unitPrice = unitPrice;
        return entity;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }
}
