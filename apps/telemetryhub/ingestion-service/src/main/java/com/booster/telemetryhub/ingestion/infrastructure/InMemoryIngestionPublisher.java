package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryIngestionPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIngestionPublisher.class);

    private final InMemoryNormalizedRawEventStore store;

    public InMemoryIngestionPublisher(InMemoryNormalizedRawEventStore store) {
        this.store = store;
    }

    public void publish(NormalizedRawEvent event) {
        store.append(event);
        log.info(
                "Published raw event to memory sink: eventType={}, deviceId={}, eventId={}, topic={}",
                event.eventType(),
                event.deviceId(),
                event.eventId(),
                event.sourceTopic()
        );
    }
}
