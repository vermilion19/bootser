package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.ingestion.config.IngestionRuntimeProperties;
import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

@Component
public class InMemoryNormalizedRawEventStore {

    private final IngestionRuntimeProperties runtimeProperties;
    private final LinkedBlockingDeque<NormalizedRawEvent> events;

    public InMemoryNormalizedRawEventStore(IngestionRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
        this.events = new LinkedBlockingDeque<>(runtimeProperties.getMaxRecentEvents());
    }

    public synchronized void append(NormalizedRawEvent event) {
        if (!runtimeProperties.isRecentEventBufferEnabled()) {
            return;
        }

        if (!events.offerFirst(event)) {
            events.pollLast();
            events.offerFirst(event);
        }
    }

    public synchronized List<NormalizedRawEvent> recent(int limit) {
        if (!runtimeProperties.isRecentEventBufferEnabled()) {
            return List.of();
        }

        List<NormalizedRawEvent> result = new ArrayList<>(Math.min(limit, events.size()));
        int count = 0;
        for (NormalizedRawEvent event : events) {
            if (count >= limit) {
                break;
            }
            result.add(event);
            count++;
        }
        return result;
    }

    public synchronized void clear() {
        if (!runtimeProperties.isRecentEventBufferEnabled()) {
            return;
        }
        events.clear();
    }
}
