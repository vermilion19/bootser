package com.booster.telemetryhub.devicesimulator.infrastructure.publisher;

import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventBatch;
import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorRuntimeState;
import com.booster.telemetryhub.devicesimulator.config.SimulatorPublisherProperties;
import com.booster.telemetryhub.devicesimulator.infrastructure.message.MessageEnvelope;
import com.booster.telemetryhub.devicesimulator.infrastructure.message.SimulationEventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Component
public class BridgeSimulationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BridgeSimulationEventPublisher.class);

    private final SimulationEventSerializer eventSerializer;
    private final SimulatorPublisherProperties publisherProperties;
    private final WebClient webClient;

    public BridgeSimulationEventPublisher(
            SimulationEventSerializer eventSerializer,
            SimulatorPublisherProperties publisherProperties
    ) {
        this.eventSerializer = eventSerializer;
        this.publisherProperties = publisherProperties;
        this.webClient = WebClient.builder().build();
    }

    public void publishBatch(SimulationEventBatch batch, SimulatorRuntimeState runtimeState) {
        List<MessageEnvelope> messages = eventSerializer.serialize(batch);

        if (!publisherProperties.getBridge().isEnabled()) {
            log.warn(
                    "Bridge publisher mode selected but bridge.enabled=false. target={}, totalMessages={}, scenario={}",
                    targetUrl(),
                    messages.size(),
                    runtimeState.scenario()
            );
            return;
        }

        BridgeBatchRequest request = new BridgeBatchRequest(
                messages.stream()
                        .map(message -> new BridgeMessageRequest(message.topic(), runtimeState.qos(), message.payload()))
                        .toList()
        );

        try {
            webClient.post()
                    .uri(targetUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(publisherProperties.getBridge().getRequestTimeoutSeconds()))
                    .block();

            log.info(
                    "Published simulated batch through ingestion bridge: target={}, scenario={}, devices={}, totalMessages={}",
                    targetUrl(),
                    runtimeState.scenario(),
                    runtimeState.deviceCount(),
                    messages.size()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to publish simulated batch through ingestion bridge: target={}, totalMessages={}, reason={}",
                    targetUrl(),
                    messages.size(),
                    exception.getMessage()
            );
        }
    }

    private String targetUrl() {
        return publisherProperties.getBridge().getIngestionBaseUrl() + publisherProperties.getBridge().getMqttBatchPath();
    }

    private record BridgeBatchRequest(
            List<BridgeMessageRequest> messages
    ) {
    }

    private record BridgeMessageRequest(
            String topic,
            int qos,
            String payload
    ) {
    }
}
