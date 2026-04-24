package com.booster.telemetryhub.devicesimulator.web.dto;

import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorBatchSummary;
import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorMetricsSnapshot;
import com.booster.telemetryhub.devicesimulator.infrastructure.mqtt.MqttPublisherSnapshot;

import java.time.Instant;

public record SimulatorMetricsResponse(
        long cycleCount,
        long telemetryCount,
        long deviceHealthCount,
        long drivingEventCount,
        Instant lastPublishedAt,
        LastBatchResponse lastBatch,
        MqttPublisherResponse mqttPublisher
) {
    public static SimulatorMetricsResponse from(
            SimulatorMetricsSnapshot snapshot,
            MqttPublisherSnapshot mqttPublisherSnapshot
    ) {
        return new SimulatorMetricsResponse(
                snapshot.cycleCount(),
                snapshot.telemetryCount(),
                snapshot.deviceHealthCount(),
                snapshot.drivingEventCount(),
                snapshot.lastPublishedAt(),
                LastBatchResponse.from(snapshot.lastBatch()),
                MqttPublisherResponse.from(mqttPublisherSnapshot)
        );
    }

    public record LastBatchResponse(
            int requestedDevices,
            int telemetryCount,
            int deviceHealthCount,
            int drivingEventCount,
            Instant generatedAt
    ) {
        static LastBatchResponse from(SimulatorBatchSummary summary) {
            return new LastBatchResponse(
                    summary.requestedDevices(),
                    summary.telemetryCount(),
                    summary.deviceHealthCount(),
                    summary.drivingEventCount(),
                    summary.generatedAt()
            );
        }
    }

    public record MqttPublisherResponse(
            String connectionState,
            String brokerUri,
            String clientId,
            int qos,
            boolean retain,
            long lastPublishedMessageCount,
            long consecutiveFailureCount,
            Instant lastAttemptAt,
            Instant lastSuccessAt,
            String lastFailureReason,
            RetryPolicyResponse retryPolicy
    ) {
        static MqttPublisherResponse from(MqttPublisherSnapshot snapshot) {
            return new MqttPublisherResponse(
                    snapshot.connectionState().name(),
                    snapshot.brokerUri(),
                    snapshot.clientId(),
                    snapshot.qos(),
                    snapshot.retain(),
                    snapshot.lastPublishedMessageCount(),
                    snapshot.consecutiveFailureCount(),
                    snapshot.lastAttemptAt(),
                    snapshot.lastSuccessAt(),
                    snapshot.lastFailureReason(),
                    new RetryPolicyResponse(
                            snapshot.retryPolicy().maxAttempts(),
                            snapshot.retryPolicy().initialDelayMs(),
                            snapshot.retryPolicy().backoffMultiplier(),
                            snapshot.retryPolicy().maxDelayMs(),
                            snapshot.retryPolicy().jitterEnabled()
                    )
            );
        }
    }

    public record RetryPolicyResponse(
            int maxAttempts,
            long initialDelayMs,
            double backoffMultiplier,
            long maxDelayMs,
            boolean jitterEnabled
    ) {
    }
}
