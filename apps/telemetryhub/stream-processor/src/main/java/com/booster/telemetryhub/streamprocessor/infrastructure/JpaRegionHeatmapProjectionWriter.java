package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.application.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.RegionHeatmapProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JpaRegionHeatmapProjectionWriter implements RegionHeatmapProjectionWriter {

    private final JdbcTemplate jdbcTemplate;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaRegionHeatmapProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void upsert(RegionHeatmapAggregate aggregate) {
        try {
            jdbcTemplate.update(
                    """
                    insert into telemetryhub_region_heatmap
                        (id, grid_lat, grid_lon, minute_bucket_start, event_count, created_at, updated_at)
                    values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                    on conflict (grid_lat, grid_lon, minute_bucket_start) do update
                        set event_count = excluded.event_count,
                            updated_at = current_timestamp
                    """,
                    com.booster.common.SnowflakeGenerator.nextId(),
                    aggregate.gridLat(),
                    aggregate.gridLon(),
                    java.sql.Timestamp.from(aggregate.minuteBucketStart()),
                    aggregate.eventCount()
            );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.REGION_HEATMAP);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.REGION_HEATMAP, exception);
            throw exception;
        }
    }
}
