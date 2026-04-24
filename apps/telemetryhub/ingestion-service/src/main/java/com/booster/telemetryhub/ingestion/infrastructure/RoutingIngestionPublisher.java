package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.ingestion.application.IngestionPublisher;
import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.config.IngestionPublisherProperties;
import org.springframework.stereotype.Component;

@Component
public class RoutingIngestionPublisher implements IngestionPublisher {

    private final LoggingIngestionPublisher loggingPublisher;
    private final InMemoryIngestionPublisher memoryPublisher;
    private final KafkaIngestionPublisher kafkaPublisher;
    private final IngestionPublisherProperties publisherProperties;

    public RoutingIngestionPublisher(
            LoggingIngestionPublisher loggingPublisher,
            InMemoryIngestionPublisher memoryPublisher,
            KafkaIngestionPublisher kafkaPublisher,
            IngestionPublisherProperties publisherProperties
    ) {
        this.loggingPublisher = loggingPublisher;
        this.memoryPublisher = memoryPublisher;
        this.kafkaPublisher = kafkaPublisher;
        this.publisherProperties = publisherProperties;
    }

    @Override
    public void publish(NormalizedRawEvent event) {
        if (publisherProperties.getMode() == IngestionPublisherProperties.PublisherMode.KAFKA) {
            kafkaPublisher.publish(event);
            return;
        }
        if (publisherProperties.getMode() == IngestionPublisherProperties.PublisherMode.MEMORY) {
            memoryPublisher.publish(event);
            return;
        }

        loggingPublisher.publish(event);
    }
}
