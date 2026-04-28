package com.booster.telemetryhub.devicesimulator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "telemetryhub.simulator.publisher")
public class SimulatorPublisherProperties {

    private PublisherMode mode = PublisherMode.LOGGING;
    private final Bridge bridge = new Bridge();
    private final Mqtt mqtt = new Mqtt();

    public enum PublisherMode {
        LOGGING,
        MEMORY,
        BRIDGE,
        MQTT
    }

    @Getter
    @Setter
    public static class Bridge {
        private boolean enabled;
        private String ingestionBaseUrl = "http://localhost:8092";
        private String mqttBatchPath = "/ingestion/v1/mqtt/messages/batch";
        private int requestTimeoutSeconds = 5;
    }

    @Getter
    @Setter
    public static class Mqtt {
        private boolean enabled;
        private boolean realClientEnabled;
        private String brokerUri = "tcp://localhost:1883";
        private String clientId = "telemetryhub-device-simulator";
        private int qos = 0;
        private boolean retain;
        private int connectionTimeoutSeconds = 5;
        private int completionTimeoutSeconds = 10;
        private final Retry retry = new Retry();
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 5;
        private long initialDelayMs = 1000;
        private double backoffMultiplier = 2.0d;
        private long maxDelayMs = 30000;
        private boolean jitterEnabled = true;
    }
}