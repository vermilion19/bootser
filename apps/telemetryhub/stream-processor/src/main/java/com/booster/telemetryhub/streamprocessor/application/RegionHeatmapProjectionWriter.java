package com.booster.telemetryhub.streamprocessor.application;

import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapAggregate;

public interface RegionHeatmapProjectionWriter {

    void upsert(RegionHeatmapAggregate aggregate);
}
