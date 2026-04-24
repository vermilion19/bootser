package com.booster.telemetryhub.devicesimulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telemetryhub.simulator.publisher")
public class SimulatorPublisherProperties {

    private PublisherMode mode = PublisherMode.LOGGING;
    private final Bridge bridge = new Bridge();
    private final Mqtt mqtt = new Mqtt();

    public PublisherMode getMode() {
        return mode;
    }

    public void setMode(PublisherMode mode) {
        this.mode = mode;
    }

    public Mqtt getMqtt() {
        return mqtt;
    }

    public Bridge getBridge() {
        return bridge;
    }

    public enum PublisherMode {
        LOGGING,
        MEMORY,
        BRIDGE,
        MQTT
    }

    public static class Bridge {
        private boolean enabled;
        private String ingestionBaseUrl = "http://localhost:8092";
        private String mqttBatchPath = "/ingestion/v1/mqtt/messages/batch";
        private int requestTimeoutSeconds = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIngestionBaseUrl() {
            return ingestionBaseUrl;
        }

        public void setIngestionBaseUrl(String ingestionBaseUrl) {
            this.ingestionBaseUrl = ingestionBaseUrl;
        }

        public String getMqttBatchPath() {
            return mqttBatchPath;
        }

        public void setMqttBatchPath(String mqttBatchPath) {
            this.mqttBatchPath = mqttBatchPath;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }
    }

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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRealClientEnabled() {
            return realClientEnabled;
        }

        public void setRealClientEnabled(boolean realClientEnabled) {
            this.realClientEnabled = realClientEnabled;
        }

        public String getBrokerUri() {
            return brokerUri;
        }

        public void setBrokerUri(String brokerUri) {
            this.brokerUri = brokerUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public int getQos() {
            return qos;
        }

        public void setQos(int qos) {
            this.qos = qos;
        }

        public boolean isRetain() {
            return retain;
        }

        public void setRetain(boolean retain) {
            this.retain = retain;
        }

        public int getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds;
        }

        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        }

        public int getCompletionTimeoutSeconds() {
            return completionTimeoutSeconds;
        }

        public void setCompletionTimeoutSeconds(int completionTimeoutSeconds) {
            this.completionTimeoutSeconds = completionTimeoutSeconds;
        }

        public Retry getRetry() {
            return retry;
        }
    }

    public static class Retry {
        private int maxAttempts = 5;
        private long initialDelayMs = 1000;
        private double backoffMultiplier = 2.0d;
        private long maxDelayMs = 30000;
        private boolean jitterEnabled = true;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }

        public boolean isJitterEnabled() {
            return jitterEnabled;
        }

        public void setJitterEnabled(boolean jitterEnabled) {
            this.jitterEnabled = jitterEnabled;
        }
    }
}
