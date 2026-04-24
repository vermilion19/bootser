package com.booster.telemetryhub.streamprocessor.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.domain.EventsPerMinuteAggregate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "telemetryhub_events_per_minute",
        indexes = {
                @Index(name = "idx_th_events_per_minute_bucket", columnList = "minute_bucket_start"),
                @Index(name = "idx_th_events_per_minute_type_bucket", columnList = "event_type, minute_bucket_start", unique = true)
        }
)
public class EventsPerMinuteEntity extends BaseEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "minute_bucket_start", nullable = false)
    private Instant minuteBucketStart;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    protected EventsPerMinuteEntity() {
    }

    public static EventsPerMinuteEntity create(EventsPerMinuteAggregate aggregate) {
        EventsPerMinuteEntity entity = new EventsPerMinuteEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.eventType = aggregate.eventType();
        entity.minuteBucketStart = aggregate.minuteBucketStart();
        entity.apply(aggregate);
        return entity;
    }

    public void apply(EventsPerMinuteAggregate aggregate) {
        eventCount = aggregate.count();
    }
}
