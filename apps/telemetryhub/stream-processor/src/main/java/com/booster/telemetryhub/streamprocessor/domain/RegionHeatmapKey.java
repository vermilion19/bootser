package com.booster.telemetryhub.streamprocessor.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record RegionHeatmapKey(
        double gridLat,
        double gridLon,
        Instant minuteBucketStart
) {
    public static RegionHeatmapKey of(double lat, double lon, double gridSize, Instant eventTime) {
        return new RegionHeatmapKey(
                floorToGrid(lat, gridSize),
                floorToGrid(lon, gridSize),
                eventTime.truncatedTo(ChronoUnit.MINUTES)
        );
    }

    private static double floorToGrid(double value, double gridSize) {
        return Math.floor(value / gridSize) * gridSize;
    }
}
