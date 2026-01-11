package com.booster.waitingservice.waiting.domain.outbox;

import com.booster.common.SnowflakeGenerator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private Long id;

    private String aggregateType; // 예: RESTAURANT
    private Long aggregateId;     // 예: 1 (식당 ID)
    private String eventType;     // 예: RESTAURANT_UPDATED

    @Lob // JSON 데이터가 들어감
    private String payload;

    private boolean published;    // 발행 여부 (false -> true)
    private LocalDateTime createdAt;

    @Builder
    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        this.id = SnowflakeGenerator.nextId();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = false; // 기본값: 미발행
        this.createdAt = LocalDateTime.now();
    }

    public void publish() {
        this.published = true;
    }
}
