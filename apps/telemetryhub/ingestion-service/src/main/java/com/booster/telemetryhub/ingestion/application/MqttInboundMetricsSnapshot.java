package com.booster.telemetryhub.ingestion.application;

import java.time.Instant;

public record MqttInboundMetricsSnapshot(
        long totalMessages,
        long totalBatches,
        Instant lastReceivedAt,
        String lastTopic,
        int lastQos
) {
    public static MqttInboundMetricsSnapshot empty() {
        return new MqttInboundMetricsSnapshot(0, 0, null, null, 0);
    }
}
