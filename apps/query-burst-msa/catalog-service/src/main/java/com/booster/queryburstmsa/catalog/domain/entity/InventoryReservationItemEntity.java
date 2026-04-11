package com.booster.queryburstmsa.catalog.domain.entity;

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
        name = "inventory_reservation_item",
        indexes = {
                @Index(name = "idx_inventory_reservation_item_reservation_id", columnList = "reservation_id"),
                @Index(name = "idx_inventory_reservation_item_product_id", columnList = "product_id")
        }
)
public class InventoryReservationItemEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservationEntity reservation;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    protected InventoryReservationItemEntity() {
    }

    public static InventoryReservationItemEntity create(InventoryReservationEntity reservation, Long productId, int quantity) {
        InventoryReservationItemEntity entity = new InventoryReservationItemEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.reservation = reservation;
        entity.productId = productId;
        entity.quantity = quantity;
        return entity;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
