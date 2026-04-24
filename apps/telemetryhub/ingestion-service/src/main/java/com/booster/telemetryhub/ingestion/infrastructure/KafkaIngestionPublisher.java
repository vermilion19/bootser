package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.common.JsonUtils;
import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.config.IngestionPublisherProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaIngestionPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaIngestionPublisher.class);

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final IngestionPublisherProperties publisherProperties;

    public KafkaIngestionPublisher(
            KafkaTemplate<String, String> stringKafkaTemplate,
            IngestionPublisherProperties publisherProperties
    ) {
        this.stringKafkaTemplate = stringKafkaTemplate;
        this.publisherProperties = publisherProperties;
    }

    public void publish(NormalizedRawEvent event) {
        if (!publisherProperties.getKafka().isEnabled()) {
            log.warn(
                    "Kafka publisher mode selected but kafka.enabled=false. topic={}, eventId={}, deviceId={}",
                    publisherProperties.getKafka().getRawTopic(),
                    event.eventId(),
                    event.deviceId()
            );
            return;
        }

        stringKafkaTemplate.send(
                publisherProperties.getKafka().getRawTopic(),
                event.kafkaKey(),
                JsonUtils.toJson(event)
        );
    }
}
