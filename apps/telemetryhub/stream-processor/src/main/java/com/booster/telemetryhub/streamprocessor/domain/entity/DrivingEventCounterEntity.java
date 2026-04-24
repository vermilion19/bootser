package com.booster.telemetryhub.streamprocessor.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;
import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterAggregate;
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
        name = "telemetryhub_driving_event_counter",
        indexes = {
                @Index(name = "idx_th_driving_event_counter_bucket", columnList = "minute_bucket_start"),
                @Index(
                        name = "idx_th_driving_event_counter_unique",
                        columnList = "device_id, driving_event_type, minute_bucket_start",
                        unique = true
                )
        }
)
public class DrivingEventCounterEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "driving_event_type", nullable = false, length = 50)
    private DrivingEventType drivingEventType;

    @Column(name = "minute_bucket_start", nullable = false)
    private Instant minuteBucketStart;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    protected DrivingEventCounterEntity() {
    }

    public static DrivingEventCounterEntity create(DrivingEventCounterAggregate aggregate) {
        DrivingEventCounterEntity entity = new DrivingEventCounterEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.deviceId = aggregate.deviceId();
        entity.drivingEventType = aggregate.drivingEventType();
        entity.minuteBucketStart = aggregate.minuteBucketStart();
        entity.apply(aggregate);
        return entity;
    }

    public void apply(DrivingEventCounterAggregate aggregate) {
        eventCount = aggregate.count();
    }
}
