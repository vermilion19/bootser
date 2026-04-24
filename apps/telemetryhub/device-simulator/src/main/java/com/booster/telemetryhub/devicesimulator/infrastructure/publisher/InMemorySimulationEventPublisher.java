package com.booster.telemetryhub.devicesimulator.infrastructure.publisher;

import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventBatch;
import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorRuntimeState;
import com.booster.telemetryhub.devicesimulator.infrastructure.message.SimulationEventSerializer;
import com.booster.telemetryhub.devicesimulator.infrastructure.store.InMemoryPublishedMessageStore;
import com.booster.telemetryhub.devicesimulator.infrastructure.store.MemoryPublishedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class InMemorySimulationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemorySimulationEventPublisher.class);

    private final SimulationEventSerializer eventSerializer;
    private final InMemoryPublishedMessageStore messageStore;

    public InMemorySimulationEventPublisher(
            SimulationEventSerializer eventSerializer,
            InMemoryPublishedMessageStore messageStore
    ) {
        this.eventSerializer = eventSerializer;
        this.messageStore = messageStore;
    }

    public void publishBatch(SimulationEventBatch batch, SimulatorRuntimeState runtimeState) {
        Instant publishedAt = Instant.now();
        List<MemoryPublishedMessage> messages = eventSerializer.serialize(batch).stream()
                .map(message -> new MemoryPublishedMessage(
                        message.topic(),
                        message.key(),
                        message.payload(),
                        publishedAt
                ))
                .toList();

        messageStore.append(messages);

        log.info(
                "Published simulated batch to memory: scenario={}, devices={}, storedMessages={}, storeSize={}",
                runtimeState.scenario(),
                runtimeState.deviceCount(),
                messages.size(),
                messageStore.size()
        );
    }
}
