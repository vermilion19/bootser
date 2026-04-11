package com.booster.queryburstmsa.catalog.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationStatus;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "inventory_reservation",
        indexes = {
                @Index(name = "idx_inventory_reservation_order_id", columnList = "order_id"),
                @Index(name = "idx_inventory_reservation_status", columnList = "status")
        }
)
public class InventoryReservationEntity extends BaseEntity {

    @Id
    private String id;

    @Column(name = "request_id", nullable = false, unique = true, length = 100)
    private String requestId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryReservationStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InventoryReservationItemEntity> items = new ArrayList<>();

    protected InventoryReservationEntity() {
    }

    public static InventoryReservationEntity create(String requestId, Long orderId, Long memberId) {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.id = String.valueOf(SnowflakeGenerator.nextId());
        entity.requestId = requestId;
        entity.orderId = orderId;
        entity.memberId = memberId;
        entity.status = InventoryReservationStatus.RESERVED;
        entity.expiresAt = LocalDateTime.now().plusMinutes(15);
        return entity;
    }

    public void addItem(Long productId, int quantity) {
        items.add(InventoryReservationItemEntity.create(this, productId, quantity));
    }

    public void release() {
        status = InventoryReservationStatus.RELEASED;
    }

    public void commit() {
        status = InventoryReservationStatus.COMMITTED;
    }

    public String getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public InventoryReservationStatus getStatus() {
        return status;
    }

    public List<InventoryReservationItemEntity> getItems() {
        return items;
    }
}
