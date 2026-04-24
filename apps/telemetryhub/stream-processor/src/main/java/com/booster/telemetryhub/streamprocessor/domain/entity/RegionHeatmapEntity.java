package com.booster.telemetryhub.streamprocessor.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapAggregate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "telemetryhub_region_heatmap",
        indexes = {
                @Index(name = "idx_th_region_heatmap_bucket", columnList = "minute_bucket_start"),
                @Index(name = "idx_th_region_heatmap_grid_bucket", columnList = "grid_lat, grid_lon, minute_bucket_start", unique = true)
        }
)
public class RegionHeatmapEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "grid_lat", nullable = false)
    private double gridLat;

    @Column(name = "grid_lon", nullable = false)
    private double gridLon;

    @Column(name = "minute_bucket_start", nullable = false)
    private Instant minuteBucketStart;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    protected RegionHeatmapEntity() {
    }

    public static RegionHeatmapEntity create(RegionHeatmapAggregate aggregate) {
        RegionHeatmapEntity entity = new RegionHeatmapEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.gridLat = aggregate.gridLat();
        entity.gridLon = aggregate.gridLon();
        entity.minuteBucketStart = aggregate.minuteBucketStart();
        entity.apply(aggregate);
        return entity;
    }

    public void apply(RegionHeatmapAggregate aggregate) {
        eventCount = aggregate.eventCount();
    }
}
