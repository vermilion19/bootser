package com.booster.telemetryhub.batchbackfill.infrastructure.target;

import com.booster.common.JsonUtils;
import com.booster.common.SnowflakeGenerator;
import com.booster.telemetryhub.batchbackfill.application.io.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.domain.BackfillOverwriteMode;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class DrivingEventCounterBackfillWriter {

    private final JdbcTemplate jdbcTemplate;

    public DrivingEventCounterBackfillWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long write(List<BackfillRawEvent> events, BackfillPlan plan) {
        if (plan.overwriteMode() == BackfillOverwriteMode.OVERWRITE) {
            jdbcTemplate.update(
                    "delete from telemetryhub_driving_event_counter where minute_bucket_start between ? and ?",
                    Timestamp.from(plan.from()),
                    Timestamp.from(plan.to())
            );
        }

        Map<Key, Long> aggregates = events.stream()
                .filter(event -> event.eventType() == EventType.DRIVING_EVENT)
                .map(event -> {
                    DrivingEvent drivingEvent = JsonUtils.fromJson(event.payload(), DrivingEvent.class);
                    return new Key(
                            event.deviceId(),
                            drivingEvent.type().name(),
                            event.eventTime().truncatedTo(ChronoUnit.MINUTES)
                    );
                })
                .collect(java.util.stream.Collectors.groupingBy(
                        key -> key,
                        java.util.stream.Collectors.counting()
                ));

        long writes = 0;
        for (Map.Entry<Key, Long> entry : aggregates.entrySet()) {
            Key key = entry.getKey();
            Long count = entry.getValue();
            Integer existing = jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from telemetryhub_driving_event_counter
                     where device_id = ?
                       and driving_event_type = ?
                       and minute_bucket_start = ?
                    """,
                    Integer.class,
                    key.deviceId(),
                    key.drivingEventType(),
                    Timestamp.from(key.minuteBucketStart())
            );

            if (existing != null && existing > 0) {
                if (plan.overwriteMode() == BackfillOverwriteMode.SKIP_EXISTING) {
                    continue;
                }

                jdbcTemplate.update(
                        """
                        update telemetryhub_driving_event_counter
                           set event_count = ?,
                               updated_at = current_timestamp
                         where device_id = ?
                           and driving_event_type = ?
                           and minute_bucket_start = ?
                        """,
                        count,
                        key.deviceId(),
                        key.drivingEventType(),
                        Timestamp.from(key.minuteBucketStart())
                );
            } else {
                jdbcTemplate.update(
                        """
                        insert into telemetryhub_driving_event_counter
                            (id, device_id, driving_event_type, minute_bucket_start, event_count, created_at, updated_at)
                        values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                        SnowflakeGenerator.nextId(),
                        key.deviceId(),
                        key.drivingEventType(),
                        Timestamp.from(key.minuteBucketStart()),
                        count
                );
            }
            writes++;
        }
        return writes;
    }

    private record Key(String deviceId, String drivingEventType, Instant minuteBucketStart) {
    }
}
