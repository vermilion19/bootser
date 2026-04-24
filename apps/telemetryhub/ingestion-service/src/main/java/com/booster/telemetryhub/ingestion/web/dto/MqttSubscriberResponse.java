package com.booster.telemetryhub.ingestion.web.dto;

import com.booster.telemetryhub.ingestion.infrastructure.MqttSubscriberSnapshot;

import java.time.Instant;
import java.util.List;

public record MqttSubscriberResponse(
        String state,
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
    public static MqttSubscriberResponse from(MqttSubscriberSnapshot snapshot) {
        return new MqttSubscriberResponse(
                snapshot.state().name(),
                snapshot.brokerUri(),
                snapshot.clientId(),
                snapshot.subscriptions(),
                snapshot.totalMessages(),
                snapshot.queuedMessages(),
                snapshot.droppedMessages(),
                snapshot.lastReceivedAt(),
                snapshot.lastTopic(),
                snapshot.lastError()
        );
    }
}
