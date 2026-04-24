package com.booster.telemetryhub.ingestion.config.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "telemetryhub.ingestion.mqtt")
public class IngestionMqttSubscriberProperties {

    private boolean enabled;
    private boolean realClientEnabled;
    private String brokerUri = "tcp://localhost:1883";
    private String clientId = "telemetryhub-ingestion-service";
    private String clientIdSuffix;
    private int connectionTimeoutSeconds = 5;
    private boolean sharedSubscriptionEnabled;
    private String sharedSubscriptionGroup = "telemetryhub-ingestion";
    private int inboundQueueCapacity = 10_000;
    private int inboundWorkerThreads = 1;
    private final List<String> subscriptions = new ArrayList<>(List.of(
            "telemetryhub/devices/+/telemetry",
            "telemetryhub/devices/+/device-health",
            "telemetryhub/devices/+/driving-event"
    ));

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

    public String getClientIdSuffix() {
        return clientIdSuffix;
    }

    public void setClientIdSuffix(String clientIdSuffix) {
        this.clientIdSuffix = clientIdSuffix;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public boolean isSharedSubscriptionEnabled() {
        return sharedSubscriptionEnabled;
    }

    public void setSharedSubscriptionEnabled(boolean sharedSubscriptionEnabled) {
        this.sharedSubscriptionEnabled = sharedSubscriptionEnabled;
    }

    public String getSharedSubscriptionGroup() {
        return sharedSubscriptionGroup;
    }

    public void setSharedSubscriptionGroup(String sharedSubscriptionGroup) {
        this.sharedSubscriptionGroup = sharedSubscriptionGroup;
    }

    public int getInboundQueueCapacity() {
        return inboundQueueCapacity;
    }

    public void setInboundQueueCapacity(int inboundQueueCapacity) {
        this.inboundQueueCapacity = inboundQueueCapacity;
    }

    public int getInboundWorkerThreads() {
        return inboundWorkerThreads;
    }

    public void setInboundWorkerThreads(int inboundWorkerThreads) {
        this.inboundWorkerThreads = inboundWorkerThreads;
    }

    public List<String> getSubscriptions() {
        return subscriptions;
    }
}
