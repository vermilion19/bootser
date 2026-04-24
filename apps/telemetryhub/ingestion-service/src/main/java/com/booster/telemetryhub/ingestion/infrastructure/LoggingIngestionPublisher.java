package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingIngestionPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingIngestionPublisher.class);

    public void publish(NormalizedRawEvent event) {
        log.info(
                "Published raw event to logging sink: eventType={}, deviceId={}, eventId={}, topic={}, kafkaKey={}",
                event.eventType(),
                event.deviceId(),
                event.eventId(),
                event.sourceTopic(),
                event.kafkaKey()
        );
    }
}
