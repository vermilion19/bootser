package com.booster.telemetryhub.devicesimulator.infrastructure;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class InMemoryPublishedMessageStore {

    private static final int MAX_MESSAGES = 500;

    private final Deque<MemoryPublishedMessage> messages = new ConcurrentLinkedDeque<>();

    public void append(List<MemoryPublishedMessage> batch) {
        for (MemoryPublishedMessage message : batch) {
            messages.addFirst(message);
        }

        while (messages.size() > MAX_MESSAGES) {
            messages.pollLast();
        }
    }

    public List<MemoryPublishedMessage> recent(int limit) {
        List<MemoryPublishedMessage> result = new ArrayList<>(Math.min(limit, messages.size()));
        int count = 0;
        for (MemoryPublishedMessage message : messages) {
            if (count >= limit) {
                break;
            }
            result.add(message);
            count++;
        }
        return result;
    }

    public int size() {
        return messages.size();
    }

    public void clear() {
        messages.clear();
    }
}
