package com.booster.queryburst.order.domain.outbox;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox 패턴 이벤트 테이블.
 *
 * 목적: 주문 트랜잭션과 Kafka 발행의 원자성 보장
 *   - Orders INSERT + OutboxEvent INSERT → 같은 트랜잭션 (All or Nothing)
 *   - OutboxMessageRelay가 3초마다 PENDING 이벤트를 Kafka로 발행
 *
 * 인덱스:
 *   - (status, created_at): PENDING 이벤트 조회 시 사용
 *     → 발행 완료(PUBLISHED)된 이벤트는 거의 접근 안 함
 *
 * 운영 고려사항:
 *   - PUBLISHED 이벤트는 주기적으로 purge 필요 (예: 7일 이상 된 것)
 *   - FAILED 이벤트는 Dead Letter Queue 또는 관리자 대시보드에서 수동 재처리
 */
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

    /** 집계 루트 타입 (예: "ORDER") */
    @Column(nullable = false, length = 50)
    private String aggregateType;

    /** 집계 루트 ID (예: orderId) */
    @Column(nullable = false)
    private Long aggregateId;

    /** 이벤트 타입 (예: "ORDER_CREATED", "ORDER_CANCELED") */
    @Column(nullable = false, length = 50)
    private String eventType;

    /** JSON 직렬화된 OrderEventPayload */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    /** Kafka 발행 실패 누적 횟수. 3회 초과 시 FAILED로 전환 */
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

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /** 재시도 횟수 증가. 3회 초과 시 FAILED로 전환 */
    public void incrementRetry() {
        this.retryCount++;
        if (this.retryCount >= 3) {
            this.status = OutboxStatus.FAILED;
        }
    }
}
