package com.booster.telemetryhub.devicesimulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telemetryhub.simulator.runtime")
public class SimulatorRuntimeProperties {

    private boolean publishedMessageBufferEnabled = true;
    private int maxPublishedMessages = 500;

    public boolean isPublishedMessageBufferEnabled() {
        return publishedMessageBufferEnabled;
    }

    public void setPublishedMessageBufferEnabled(boolean publishedMessageBufferEnabled) {
        this.publishedMessageBufferEnabled = publishedMessageBufferEnabled;
    }

    public int getMaxPublishedMessages() {
        return maxPublishedMessages;
    }

    public void setMaxPublishedMessages(int maxPublishedMessages) {
        this.maxPublishedMessages = maxPublishedMessages;
    }
}
