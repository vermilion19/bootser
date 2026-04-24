package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.common.SnowflakeGenerator;
import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillRawEvent;
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
            Integer existing = jdbcTemplate.queryForObject(
                    "select count(*) from telemetryhub_device_last_seen where device_id = ?",
                    Integer.class,
                    event.deviceId()
            );

            if (existing != null && existing > 0) {
                if (plan.overwriteMode() == BackfillOverwriteMode.SKIP_EXISTING) {
                    continue;
                }

                jdbcTemplate.update(
                        """
                        update telemetryhub_device_last_seen
                           set last_event_id = ?,
                               last_event_type = ?,
                               last_event_time = ?,
                               last_ingest_time = ?,
                               source_topic = ?
                         where device_id = ?
                        """,
                        event.eventId(),
                        event.eventType().name(),
                        Timestamp.from(event.eventTime()),
                        Timestamp.from(event.eventTime()),
                        "batch-backfill",
                        event.deviceId()
                );
            } else {
                jdbcTemplate.update(
                        """
                        insert into telemetryhub_device_last_seen
                            (id, device_id, last_event_id, last_event_type, last_event_time, last_ingest_time, source_topic, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                        SnowflakeGenerator.nextId(),
                        event.deviceId(),
                        event.eventId(),
                        event.eventType().name(),
                        Timestamp.from(event.eventTime()),
                        Timestamp.from(event.eventTime()),
                        "batch-backfill"
                );
            }
            writes++;
        }
        return writes;
    }

    private BackfillRawEvent pickLatest(BackfillRawEvent left, BackfillRawEvent right) {
        return Comparator.comparing(BackfillRawEvent::eventTime).compare(left, right) >= 0 ? left : right;
    }
}
