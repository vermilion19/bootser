package com.booster.telemetryhub.streamprocessor.application.plan;

import java.util.List;

public record StreamTopologyPlan(
        String applicationId,
        String sourceTopic,
        List<AggregationPlan> aggregations
) {
}
