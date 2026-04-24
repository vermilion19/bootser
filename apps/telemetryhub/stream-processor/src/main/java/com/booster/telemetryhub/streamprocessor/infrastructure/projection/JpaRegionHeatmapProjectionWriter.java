package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.telemetryhub.streamprocessor.application.metrics.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.application.projection.RegionHeatmapProjectionWriter;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class JpaRegionHeatmapProjectionWriter
        extends BufferedJdbcProjectionWriter<RegionHeatmapAggregate, String>
        implements RegionHeatmapProjectionWriter {

    private static final String UPSERT_SQL = """
            insert into telemetryhub_region_heatmap
                (id, grid_lat, grid_lon, minute_bucket_start, event_count, created_at, updated_at)
            values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
            on conflict (grid_lat, grid_lon, minute_bucket_start) do update
                set event_count = excluded.event_count,
                    updated_at = current_timestamp
            """;

    public JpaRegionHeatmapProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector,
            StreamProcessorProperties properties
    ) {
        super(jdbcTemplate, metricsCollector, properties, ProjectionType.REGION_HEATMAP);
    }

    @Override
    public void upsert(RegionHeatmapAggregate aggregate) {
        upsertBuffered(aggregate);
    }

    @Override
    protected String bufferKey(RegionHeatmapAggregate aggregate) {
        return aggregate.gridLat() + ":" + aggregate.gridLon() + ":" + aggregate.minuteBucketStart();
    }

    @Override
    protected RegionHeatmapAggregate mergeAggregates(RegionHeatmapAggregate left, RegionHeatmapAggregate right) {
        return right.eventCount() >= left.eventCount() ? right : left;
    }

    @Override
    protected void batchUpsert(JdbcTemplate jdbcTemplate, List<RegionHeatmapAggregate> aggregates) {
        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                aggregates,
                aggregates.size(),
                this::bindStatement
        );
    }

    private void bindStatement(PreparedStatement statement, RegionHeatmapAggregate aggregate) throws SQLException {
        statement.setLong(1, com.booster.common.SnowflakeGenerator.nextId());
        statement.setDouble(2, aggregate.gridLat());
        statement.setDouble(3, aggregate.gridLon());
        statement.setTimestamp(4, Timestamp.from(aggregate.minuteBucketStart()));
        statement.setLong(5, aggregate.eventCount());
    }
}
