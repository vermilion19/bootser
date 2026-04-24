package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.BackfillSourceReader;
import com.booster.telemetryhub.contracts.common.EventType;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

@Component
public class StubBackfillSourceReader implements BackfillSourceReader {

    @Override
    public void readChunks(BackfillPlan plan, Consumer<List<BackfillRawEvent>> chunkConsumer) {
        chunkConsumer.accept(List.of(
                new BackfillRawEvent(
                        EventType.TELEMETRY,
                        "backfill-telemetry-1",
                        "device-001",
                        plan.from().plusSeconds(30),
                        plan.from().plusSeconds(35),
                        "{\"stub\":true,\"type\":\"telemetry\"}"
                ),
                new BackfillRawEvent(
                        EventType.DEVICE_HEALTH,
                        "backfill-health-1",
                        "device-001",
                        plan.from().plusSeconds(45),
                        plan.from().plusSeconds(50),
                        "{\"stub\":true,\"type\":\"device_health\"}"
                ),
                new BackfillRawEvent(
                        EventType.DRIVING_EVENT,
                        "backfill-driving-1",
                        "device-002",
                        plan.from().plusSeconds(55),
                        plan.from().plusSeconds(60),
                        "{\"stub\":true,\"type\":\"driving_event\"}"
                )
        ));
    }
}
