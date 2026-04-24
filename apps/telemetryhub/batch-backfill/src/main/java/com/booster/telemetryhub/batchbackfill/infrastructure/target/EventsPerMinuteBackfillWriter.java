package com.booster.telemetryhub.batchbackfill.infrastructure.target;

import com.booster.common.SnowflakeGenerator;
import com.booster.telemetryhub.batchbackfill.application.io.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.domain.BackfillOverwriteMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class EventsPerMinuteBackfillWriter {

    private final JdbcTemplate jdbcTemplate;

    public EventsPerMinuteBackfillWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long write(List<BackfillRawEvent> events, BackfillPlan plan) {
        if (plan.overwriteMode() == BackfillOverwriteMode.OVERWRITE) {
            jdbcTemplate.update(
                    "delete from telemetryhub_events_per_minute where minute_bucket_start between ? and ?",
                    Timestamp.from(plan.from()),
                    Timestamp.from(plan.to())
            );
        }

        Map<Key, Long> aggregates = events.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        event -> new Key(event.eventType().name(), event.eventTime().truncatedTo(ChronoUnit.MINUTES)),
                        java.util.stream.Collectors.counting()
                ));

        long writes = 0;
        for (Map.Entry<Key, Long> entry : aggregates.entrySet()) {
            Key key = entry.getKey();
            Long count = entry.getValue();
            Integer existing = jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from telemetryhub_events_per_minute
                     where event_type = ?
                       and minute_bucket_start = ?
                    """,
                    Integer.class,
                    key.eventType(),
                    Timestamp.from(key.minuteBucketStart())
            );

            if (existing != null && existing > 0) {
                if (plan.overwriteMode() == BackfillOverwriteMode.SKIP_EXISTING) {
                    continue;
                }

                jdbcTemplate.update(
                        """
                        update telemetryhub_events_per_minute
                           set event_count = ?,
                               updated_at = current_timestamp
                         where event_type = ?
                           and minute_bucket_start = ?
                        """,
                        count,
                        key.eventType(),
                        Timestamp.from(key.minuteBucketStart())
                );
            } else {
                jdbcTemplate.update(
                        """
                        insert into telemetryhub_events_per_minute
                            (id, event_type, minute_bucket_start, event_count, created_at, updated_at)
                        values (?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                        SnowflakeGenerator.nextId(),
                        key.eventType(),
                        Timestamp.from(key.minuteBucketStart()),
                        count
                );
            }
            writes++;
        }
        return writes;
    }

    private record Key(String eventType, Instant minuteBucketStart) {
    }
}
