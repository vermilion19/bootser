package com.booster.telemetryhub.analyticsapi.application.query;

import java.time.Instant;

public record RegionHeatmapView(
        double gridLat,
        double gridLon,
        Instant minuteBucketStart,
        long eventCount
) {
}
