package com.booster.telemetryhub.streamprocessor.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;
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
        name = "telemetryhub_device_last_seen",
        indexes = {
                @Index(name = "idx_th_device_last_seen_device_id", columnList = "device_id", unique = true),
                @Index(name = "idx_th_device_last_seen_event_time", columnList = "last_event_time")
        }
)
public class DeviceLastSeenEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true, length = 100)
    private String deviceId;

    @Column(name = "last_event_id", nullable = false, length = 100)
    private String lastEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_event_type", nullable = false, length = 50)
    private EventType lastEventType;

    @Column(name = "last_event_time", nullable = false)
    private Instant lastEventTime;

    @Column(name = "last_ingest_time", nullable = false)
    private Instant lastIngestTime;

    @Column(name = "source_topic", nullable = false, length = 200)
    private String sourceTopic;

    protected DeviceLastSeenEntity() {
    }

    public static DeviceLastSeenEntity create(DeviceLastSeenAggregate aggregate) {
        DeviceLastSeenEntity entity = new DeviceLastSeenEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.deviceId = aggregate.deviceId();
        entity.apply(aggregate);
        return entity;
    }

    public void apply(DeviceLastSeenAggregate aggregate) {
        lastEventId = aggregate.lastEventId();
        lastEventType = aggregate.lastEventType();
        lastEventTime = aggregate.lastEventTime();
        lastIngestTime = aggregate.lastIngestTime();
        sourceTopic = aggregate.sourceTopic();
    }
}
