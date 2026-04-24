package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.ingestion.config.IngestionPublisherProperties;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventKeyResolver {

    private final IngestionPublisherProperties publisherProperties;

    public KafkaEventKeyResolver(IngestionPublisherProperties publisherProperties) {
        this.publisherProperties = publisherProperties;
    }

    public String resolve(EventType eventType, String eventId, String deviceId) {
        return switch (publisherProperties.getKafka().getKeyStrategy()) {
            case DEVICE_ID -> deviceId;
            case EVENT_ID -> eventId;
            case EVENT_TYPE_AND_DEVICE_ID -> eventType.name() + ":" + deviceId;
        };
    }
}
