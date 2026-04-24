package com.booster.telemetryhub.streamprocessor.application.plan;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.domain.AggregationType;

import java.time.Duration;
import java.util.List;

public record AggregationPlan(
        AggregationType aggregationType,
        List<EventType> sourceEventTypes,
        Duration window,
        Duration gracePeriod,
        String stateStoreName,
        String targetTable,
        String description
) {
}
