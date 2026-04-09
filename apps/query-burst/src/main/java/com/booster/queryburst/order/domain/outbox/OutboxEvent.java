package com.booster.queryburst.order.domain.outbox;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime publishedAt;

    public static OutboxEvent create(String aggregateType, Long aggregateId,
                                     String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.id = SnowflakeGenerator.nextId();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;
        return event;
    }

    public void markSending() {
        this.status = OutboxStatus.SENDING;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markPublishFailed() {
        this.retryCount++;
        if (this.retryCount >= 3) {
            this.status = OutboxStatus.FAILED;
            return;
        }
        this.status = OutboxStatus.PENDING;
    }

    public void retry() {
        if (this.status != OutboxStatus.FAILED) {
            throw new IllegalStateException("FAILED 상태의 이벤트만 재처리할 수 있습니다.");
        }
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.publishedAt = null;
    }
}
