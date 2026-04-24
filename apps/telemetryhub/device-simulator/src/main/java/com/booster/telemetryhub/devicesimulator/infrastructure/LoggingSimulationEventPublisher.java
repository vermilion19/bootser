package com.booster.telemetryhub.devicesimulator.infrastructure;

import com.booster.telemetryhub.devicesimulator.application.SimulationEventBatch;
import com.booster.telemetryhub.devicesimulator.application.SimulatorRuntimeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoggingSimulationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingSimulationEventPublisher.class);
    private final SimulationEventSerializer eventSerializer;

    public LoggingSimulationEventPublisher(SimulationEventSerializer eventSerializer) {
        this.eventSerializer = eventSerializer;
    }

    public void publishBatch(SimulationEventBatch batch, SimulatorRuntimeState runtimeState) {
        List<MessageEnvelope> messages = eventSerializer.serialize(batch);
        MessageEnvelope sample = messages.isEmpty() ? null : messages.get(0);

        log.info(
                "Published simulated batch: scenario={}, devices={}, telemetry={}, deviceHealth={}, drivingEvents={}, total={}, sampleTopic={}, samplePayloadSize={}",
                runtimeState.scenario(),
                runtimeState.deviceCount(),
                batch.telemetryEvents().size(),
                batch.deviceHealthEvents().size(),
                batch.drivingEvents().size(),
                batch.totalCount(),
                sample != null ? sample.topic() : null,
                sample != null ? sample.payload().length() : 0
        );
    }
}
