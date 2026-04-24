package com.booster.telemetryhub.ingestion.config.publisher;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telemetryhub.ingestion.publisher")
public class IngestionPublisherProperties {

    private PublisherMode mode = PublisherMode.MEMORY;
    private final Kafka kafka = new Kafka();

    public PublisherMode getMode() {
        return mode;
    }

    public void setMode(PublisherMode mode) {
        this.mode = mode;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public enum PublisherMode {
        LOGGING,
        MEMORY,
        KAFKA
    }

    public static class Kafka {
        private boolean enabled;
        private String rawTopic = "telemetryhub.raw-events";
        private KeyStrategy keyStrategy = KeyStrategy.DEVICE_ID;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRawTopic() {
            return rawTopic;
        }

        public void setRawTopic(String rawTopic) {
            this.rawTopic = rawTopic;
        }

        public KeyStrategy getKeyStrategy() {
            return keyStrategy;
        }

        public void setKeyStrategy(KeyStrategy keyStrategy) {
            this.keyStrategy = keyStrategy;
        }
    }

    public enum KeyStrategy {
        DEVICE_ID,
        EVENT_ID,
        EVENT_TYPE_AND_DEVICE_ID
    }
}
