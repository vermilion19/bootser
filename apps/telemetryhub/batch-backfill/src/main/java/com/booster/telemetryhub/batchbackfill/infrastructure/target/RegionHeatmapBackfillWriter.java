package com.booster.telemetryhub.batchbackfill.infrastructure.target;

import com.booster.common.JsonUtils;
import com.booster.common.SnowflakeGenerator;
import com.booster.telemetryhub.batchbackfill.application.io.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.config.BatchBackfillProperties;
import com.booster.telemetryhub.batchbackfill.domain.BackfillOverwriteMode;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class RegionHeatmapBackfillWriter {

    private final JdbcTemplate jdbcTemplate;
    private final BatchBackfillProperties properties;

    public RegionHeatmapBackfillWriter(JdbcTemplate jdbcTemplate, BatchBackfillProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public long write(List<BackfillRawEvent> events, BackfillPlan plan) {
        if (plan.overwriteMode() == BackfillOverwriteMode.OVERWRITE) {
            jdbcTemplate.update(
                    "delete from telemetryhub_region_heatmap where minute_bucket_start between ? and ?",
                    Timestamp.from(plan.from()),
                    Timestamp.from(plan.to())
            );
        }

        Map<Key, Long> aggregates = events.stream()
                .filter(event -> event.eventType() == EventType.TELEMETRY)
                .map(event -> {
                    TelemetryEvent telemetryEvent = JsonUtils.fromJson(event.payload(), TelemetryEvent.class);
                    return new Key(
                            floorToGrid(telemetryEvent.lat()),
                            floorToGrid(telemetryEvent.lon()),
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
                      from telemetryhub_region_heatmap
                     where grid_lat = ?
                       and grid_lon = ?
                       and minute_bucket_start = ?
                    """,
                    Integer.class,
                    key.gridLat(),
                    key.gridLon(),
                    Timestamp.from(key.minuteBucketStart())
            );

            if (existing != null && existing > 0) {
                if (plan.overwriteMode() == BackfillOverwriteMode.SKIP_EXISTING) {
                    continue;
                }

                jdbcTemplate.update(
                        """
                        update telemetryhub_region_heatmap
                           set event_count = ?,
                               updated_at = current_timestamp
                         where grid_lat = ?
                           and grid_lon = ?
                           and minute_bucket_start = ?
                        """,
                        count,
                        key.gridLat(),
                        key.gridLon(),
                        Timestamp.from(key.minuteBucketStart())
                );
            } else {
                jdbcTemplate.update(
                        """
                        insert into telemetryhub_region_heatmap
                            (id, grid_lat, grid_lon, minute_bucket_start, event_count, created_at, updated_at)
                        values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                        SnowflakeGenerator.nextId(),
                        key.gridLat(),
                        key.gridLon(),
                        Timestamp.from(key.minuteBucketStart()),
                        count
                );
            }
            writes++;
        }
        return writes;
    }

    private double floorToGrid(double value) {
        double gridSize = properties.getHeatmapGridSize();
        return Math.floor(value / gridSize) * gridSize;
    }

    private record Key(double gridLat, double gridLon, Instant minuteBucketStart) {
    }
}
