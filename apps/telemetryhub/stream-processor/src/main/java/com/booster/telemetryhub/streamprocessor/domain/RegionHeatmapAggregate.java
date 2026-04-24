package com.booster.telemetryhub.streamprocessor.domain;

import java.time.Instant;

public record RegionHeatmapAggregate(
        double gridLat,
        double gridLon,
        Instant minuteBucketStart,
        long eventCount
) {
    public static RegionHeatmapAggregate first(RegionHeatmapKey key) {
        return new RegionHeatmapAggregate(
                key.gridLat(),
                key.gridLon(),
                key.minuteBucketStart(),
                1
        );
    }

    public RegionHeatmapAggregate increment() {
        return new RegionHeatmapAggregate(
                gridLat,
                gridLon,
                minuteBucketStart,
                eventCount + 1
        );
    }
}
