package com.booster.telemetryhub.ingestion.infrastructure.publisher;

import com.booster.telemetryhub.ingestion.application.normalize.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.application.publisher.IngestionPublishResult;
import com.booster.telemetryhub.ingestion.application.publisher.IngestionPublisher;
import com.booster.telemetryhub.ingestion.config.publisher.IngestionPublisherProperties;
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
    public IngestionPublishResult publish(NormalizedRawEvent event) {
        if (publisherProperties.getMode() == IngestionPublisherProperties.PublisherMode.KAFKA) {
            return kafkaPublisher.publish(event);
        }
        if (publisherProperties.getMode() == IngestionPublisherProperties.PublisherMode.MEMORY) {
            return memoryPublisher.publish(event);
        }

        return loggingPublisher.publish(event);
    }
}
