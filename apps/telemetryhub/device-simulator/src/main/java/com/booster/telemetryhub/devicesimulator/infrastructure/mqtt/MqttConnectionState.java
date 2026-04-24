package com.booster.telemetryhub.devicesimulator.infrastructure.mqtt;

public enum MqttConnectionState {
    DISABLED,
    IDLE,
    CONNECTING,
    READY,
    DEGRADED
}
