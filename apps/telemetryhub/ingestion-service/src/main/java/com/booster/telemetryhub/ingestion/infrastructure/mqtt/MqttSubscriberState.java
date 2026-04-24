package com.booster.telemetryhub.ingestion.infrastructure.mqtt;

public enum MqttSubscriberState {
    DISABLED,
    IDLE,
    CONNECTING,
    READY,
    DEGRADED
}
