package com.booster.telemetryhub.devicesimulator.infrastructure.mqtt;

import com.booster.telemetryhub.devicesimulator.infrastructure.message.MessageEnvelope;

public interface MqttConnectionManager {

    MqttConnectionState connect();

    void disconnect();

    PublishResult publish(MessageEnvelope message);

    record PublishResult(
            boolean successful,
            String failureReason
    ) {
        public static PublishResult ok() {
            return new PublishResult(true, null);
        }

        public static PublishResult failure(String failureReason) {
            return new PublishResult(false, failureReason);
        }
    }
}
