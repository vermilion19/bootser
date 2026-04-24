package com.booster.telemetryhub.ingestion.config.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telemetryhub.ingestion.runtime")
public class IngestionRuntimeProperties {

    private boolean recentEventBufferEnabled = true;
    private int maxRecentEvents = 500;

    public boolean isRecentEventBufferEnabled() {
        return recentEventBufferEnabled;
    }

    public void setRecentEventBufferEnabled(boolean recentEventBufferEnabled) {
        this.recentEventBufferEnabled = recentEventBufferEnabled;
    }

    public int getMaxRecentEvents() {
        return maxRecentEvents;
    }

    public void setMaxRecentEvents(int maxRecentEvents) {
        this.maxRecentEvents = maxRecentEvents;
    }
}
