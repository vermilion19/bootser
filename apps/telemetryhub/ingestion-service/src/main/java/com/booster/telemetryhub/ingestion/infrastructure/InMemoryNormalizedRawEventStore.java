package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.ingestion.config.IngestionRuntimeProperties;
import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class InMemoryNormalizedRawEventStore {

    private final IngestionRuntimeProperties runtimeProperties;
    private final Deque<NormalizedRawEvent> events = new ConcurrentLinkedDeque<>();

    public InMemoryNormalizedRawEventStore(IngestionRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    public void append(NormalizedRawEvent event) {
        if (!runtimeProperties.isRecentEventBufferEnabled()) {
            return;
        }

        events.addFirst(event);
        while (events.size() > runtimeProperties.getMaxRecentEvents()) {
            events.pollLast();
        }
    }

    public List<NormalizedRawEvent> recent(int limit) {
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

    public void clear() {
        if (!runtimeProperties.isRecentEventBufferEnabled()) {
            return;
        }
        events.clear();
    }
}
