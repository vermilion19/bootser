package com.booster.telemetryhub.ingestion.infrastructure;

import java.time.Instant;
import java.util.List;

public record MqttSubscriberSnapshot(
        MqttSubscriberState state,
        String brokerUri,
        String clientId,
        List<String> subscriptions,
        long totalMessages,
        long queuedMessages,
        long droppedMessages,
        Instant lastReceivedAt,
        String lastTopic,
        String lastError
) {
}
