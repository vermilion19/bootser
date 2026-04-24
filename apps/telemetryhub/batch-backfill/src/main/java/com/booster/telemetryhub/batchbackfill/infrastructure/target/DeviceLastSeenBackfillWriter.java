package com.booster.telemetryhub.batchbackfill.infrastructure.target;

import com.booster.common.SnowflakeGenerator;
import com.booster.telemetryhub.batchbackfill.application.io.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.domain.BackfillOverwriteMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class DeviceLastSeenBackfillWriter {

    private final JdbcTemplate jdbcTemplate;

    public DeviceLastSeenBackfillWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long write(List<BackfillRawEvent> events, BackfillPlan plan) {
        Map<String, BackfillRawEvent> latestByDevice = events.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BackfillRawEvent::deviceId,
                        event -> event,
                        this::pickLatest
                ));

        long writes = 0;
        for (BackfillRawEvent event : latestByDevice.values()) {
            int updatedRows;
            if (plan.overwriteMode() == BackfillOverwriteMode.SKIP_EXISTING) {
                updatedRows = jdbcTemplate.update(
                        """
                        insert into telemetryhub_device_last_seen
                            (id, device_id, last_event_id, last_event_type, last_event_time, last_ingest_time, source_topic, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        on conflict (device_id) do nothing
                        """,
                        SnowflakeGenerator.nextId(),
                        event.deviceId(),
                        event.eventId(),
                        event.eventType().name(),
                        Timestamp.from(event.eventTime()),
                        Timestamp.from(event.ingestTime()),
                        "batch-backfill"
                );
            } else {
                updatedRows = jdbcTemplate.update(
                        """
                        insert into telemetryhub_device_last_seen
                            (id, device_id, last_event_id, last_event_type, last_event_time, last_ingest_time, source_topic, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        on conflict (device_id) do update
                            set last_event_id = excluded.last_event_id,
                                last_event_type = excluded.last_event_type,
                                last_event_time = excluded.last_event_time,
                                last_ingest_time = excluded.last_ingest_time,
                                source_topic = excluded.source_topic,
                                updated_at = current_timestamp
                        where excluded.last_event_time > telemetryhub_device_last_seen.last_event_time
                           or (
                                excluded.last_event_time = telemetryhub_device_last_seen.last_event_time
                                and excluded.last_ingest_time >= telemetryhub_device_last_seen.last_ingest_time
                           )
                        """,
                        SnowflakeGenerator.nextId(),
                        event.deviceId(),
                        event.eventId(),
                        event.eventType().name(),
                        Timestamp.from(event.eventTime()),
                        Timestamp.from(event.ingestTime()),
                        "batch-backfill"
                );
            }
            writes += updatedRows;
        }
        return writes;
    }

    private BackfillRawEvent pickLatest(BackfillRawEvent left, BackfillRawEvent right) {
        return Comparator.comparing(BackfillRawEvent::eventTime).compare(left, right) >= 0 ? left : right;
    }
}
