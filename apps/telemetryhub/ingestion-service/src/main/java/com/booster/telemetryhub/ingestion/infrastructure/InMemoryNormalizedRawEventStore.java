package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class InMemoryNormalizedRawEventStore {

    private static final int MAX_EVENTS = 500;

    private final Deque<NormalizedRawEvent> events = new ConcurrentLinkedDeque<>();

    public void append(NormalizedRawEvent event) {
        events.addFirst(event);
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }

    public List<NormalizedRawEvent> recent(int limit) {
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
        events.clear();
    }
}
