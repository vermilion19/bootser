package com.booster.queryburstmsa.order.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburstmsa.order.domain.OutboxStatus;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "order_outbox_event",
        indexes = {
                @Index(name = "idx_order_outbox_status_created", columnList = "status, created_at")
        }
)
public class OutboxEventEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected OutboxEventEntity() {
    }

    public static OutboxEventEntity create(Long aggregateId, String eventType, String payload) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.status = OutboxStatus.PENDING;
        entity.retryCount = 0;
        return entity;
    }

    public void markSending() {
        status = OutboxStatus.SENDING;
    }

    public void markPublished() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        retryCount++;
        status = retryCount >= 3 ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }
}
