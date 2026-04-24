package com.booster.telemetryhub.ingestion.infrastructure;

public enum MqttSubscriberState {
    DISABLED,
    IDLE,
    CONNECTING,
    READY,
    DEGRADED
}
