package com.booster.telemetryhub.ingestion.application;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MqttInboundAdapter {

    private final IngestionService ingestionService;
    private final AtomicReference<MqttInboundMetricsSnapshot> metricsRef =
            new AtomicReference<>(MqttInboundMetricsSnapshot.empty());

    public MqttInboundAdapter(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public NormalizedRawEvent receive(String topic, int qos, String payload) {
        Instant receivedAt = Instant.now();
        markMessage(topic, qos, receivedAt);
        return ingestionService.ingest(new IngestionMessage(topic, qos, payload, receivedAt));
    }

    public List<NormalizedRawEvent> receiveBatch(List<IngestionMessage> messages) {
        Instant batchReceivedAt = Instant.now();
        markBatch(messages.size(), batchReceivedAt);

        List<NormalizedRawEvent> results = new ArrayList<>(messages.size());
        for (IngestionMessage message : messages) {
            markMessage(message.topic(), message.qos(), batchReceivedAt);
            results.add(ingestionService.ingest(
                    new IngestionMessage(message.topic(), message.qos(), message.payload(), batchReceivedAt)
            ));
        }
        return results;
    }

    public MqttInboundMetricsSnapshot metrics() {
        return metricsRef.get();
    }

    private void markBatch(int size, Instant receivedAt) {
        metricsRef.updateAndGet(current -> new MqttInboundMetricsSnapshot(
                current.totalMessages(),
                current.totalBatches() + 1,
                receivedAt,
                current.lastTopic(),
                current.lastQos()
        ));
    }

    private void markMessage(String topic, int qos, Instant receivedAt) {
        metricsRef.updateAndGet(current -> new MqttInboundMetricsSnapshot(
                current.totalMessages() + 1,
                current.totalBatches(),
                receivedAt,
                topic,
                qos
        ));
    }
}
