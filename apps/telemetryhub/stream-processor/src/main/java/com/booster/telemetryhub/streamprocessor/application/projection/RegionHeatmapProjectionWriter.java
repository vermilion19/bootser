package com.booster.telemetryhub.streamprocessor.application.projection;

import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapAggregate;

public interface RegionHeatmapProjectionWriter {

    void upsert(RegionHeatmapAggregate aggregate);
}
