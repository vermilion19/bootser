package com.booster.telemetryhub.ingestion.web.dto;

import com.booster.telemetryhub.ingestion.application.mqtt.MqttInboundMetricsSnapshot;

import java.time.Instant;

public record MqttInboundMetricsResponse(
        long totalMessages,
        long totalBatches,
        Instant lastReceivedAt,
        String lastTopic,
        int lastQos
) {
    public static MqttInboundMetricsResponse from(MqttInboundMetricsSnapshot snapshot) {
        return new MqttInboundMetricsResponse(
                snapshot.totalMessages(),
                snapshot.totalBatches(),
                snapshot.lastReceivedAt(),
                snapshot.lastTopic(),
                snapshot.lastQos()
        );
    }
}
