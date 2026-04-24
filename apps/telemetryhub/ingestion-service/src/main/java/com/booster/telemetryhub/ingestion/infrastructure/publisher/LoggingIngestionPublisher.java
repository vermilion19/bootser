package com.booster.telemetryhub.ingestion.infrastructure.publisher;

import com.booster.telemetryhub.ingestion.application.normalize.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.application.publisher.IngestionPublishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingIngestionPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingIngestionPublisher.class);

    public IngestionPublishResult publish(NormalizedRawEvent event) {
        log.info(
                "Published raw event to logging sink: eventType={}, deviceId={}, eventId={}, topic={}, kafkaKey={}",
                event.eventType(),
                event.deviceId(),
                event.eventId(),
                event.sourceTopic(),
                event.kafkaKey()
        );
        return IngestionPublishResult.success("LOGGING");
    }
}
