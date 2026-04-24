package com.booster.telemetryhub.analyticsapi.web.dto;

import com.booster.telemetryhub.analyticsapi.application.query.RegionHeatmapView;

import java.time.Instant;

public record RegionHeatmapResponse(
        double gridLat,
        double gridLon,
        Instant minuteBucketStart,
        long eventCount
) {
    public static RegionHeatmapResponse from(RegionHeatmapView view) {
        return new RegionHeatmapResponse(
                view.gridLat(),
                view.gridLon(),
                view.minuteBucketStart(),
                view.eventCount()
        );
    }
}
