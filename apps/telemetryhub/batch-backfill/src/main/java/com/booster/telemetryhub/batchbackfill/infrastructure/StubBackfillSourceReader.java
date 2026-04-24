package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.BackfillSourceReader;
import com.booster.telemetryhub.contracts.common.EventType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StubBackfillSourceReader implements BackfillSourceReader {

    @Override
    public List<BackfillRawEvent> read(BackfillPlan plan) {
        return List.of(
                new BackfillRawEvent(
                        EventType.TELEMETRY,
                        "backfill-telemetry-1",
                        "device-001",
                        plan.from().plusSeconds(30),
                        "{\"stub\":true,\"type\":\"telemetry\"}"
                ),
                new BackfillRawEvent(
                        EventType.DEVICE_HEALTH,
                        "backfill-health-1",
                        "device-001",
                        plan.from().plusSeconds(45),
                        "{\"stub\":true,\"type\":\"device_health\"}"
                ),
                new BackfillRawEvent(
                        EventType.DRIVING_EVENT,
                        "backfill-driving-1",
                        "device-002",
                        plan.from().plusSeconds(55),
                        "{\"stub\":true,\"type\":\"driving_event\"}"
                )
        );
    }
}
