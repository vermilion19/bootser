package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.BackfillTargetWriter;
import com.booster.telemetryhub.batchbackfill.domain.BackfillTarget;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoutingBackfillTargetWriter implements BackfillTargetWriter {

    private final DryRunBackfillTargetWriter dryRunBackfillTargetWriter;
    private final DeviceLastSeenBackfillWriter deviceLastSeenBackfillWriter;
    private final EventsPerMinuteBackfillWriter eventsPerMinuteBackfillWriter;
    private final DrivingEventCounterBackfillWriter drivingEventCounterBackfillWriter;
    private final RegionHeatmapBackfillWriter regionHeatmapBackfillWriter;

    public RoutingBackfillTargetWriter(
            DryRunBackfillTargetWriter dryRunBackfillTargetWriter,
            DeviceLastSeenBackfillWriter deviceLastSeenBackfillWriter,
            EventsPerMinuteBackfillWriter eventsPerMinuteBackfillWriter,
            DrivingEventCounterBackfillWriter drivingEventCounterBackfillWriter,
            RegionHeatmapBackfillWriter regionHeatmapBackfillWriter
    ) {
        this.dryRunBackfillTargetWriter = dryRunBackfillTargetWriter;
        this.deviceLastSeenBackfillWriter = deviceLastSeenBackfillWriter;
        this.eventsPerMinuteBackfillWriter = eventsPerMinuteBackfillWriter;
        this.drivingEventCounterBackfillWriter = drivingEventCounterBackfillWriter;
        this.regionHeatmapBackfillWriter = regionHeatmapBackfillWriter;
    }

    @Override
    public long write(BackfillTarget target, List<BackfillRawEvent> events, BackfillPlan plan) {
        if (plan.dryRun()) {
            return dryRunBackfillTargetWriter.write(target, events, plan);
        }

        return switch (target) {
            case DEVICE_LAST_SEEN -> deviceLastSeenBackfillWriter.write(events, plan);
            case EVENTS_PER_MINUTE -> eventsPerMinuteBackfillWriter.write(events, plan);
            case DRIVING_EVENT_COUNTER -> drivingEventCounterBackfillWriter.write(events, plan);
            case REGION_HEATMAP -> regionHeatmapBackfillWriter.write(events, plan);
        };
    }
}
