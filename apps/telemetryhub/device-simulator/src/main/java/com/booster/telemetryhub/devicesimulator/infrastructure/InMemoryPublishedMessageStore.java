package com.booster.telemetryhub.devicesimulator.infrastructure;

import com.booster.telemetryhub.devicesimulator.config.SimulatorRuntimeProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class InMemoryPublishedMessageStore {

    private final SimulatorRuntimeProperties runtimeProperties;
    private final Deque<MemoryPublishedMessage> messages = new ConcurrentLinkedDeque<>();

    public InMemoryPublishedMessageStore(SimulatorRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    public void append(List<MemoryPublishedMessage> batch) {
        if (!runtimeProperties.isPublishedMessageBufferEnabled()) {
            return;
        }

        for (MemoryPublishedMessage message : batch) {
            messages.addFirst(message);
        }

        while (messages.size() > runtimeProperties.getMaxPublishedMessages()) {
            messages.pollLast();
        }
    }

    public List<MemoryPublishedMessage> recent(int limit) {
        if (!runtimeProperties.isPublishedMessageBufferEnabled()) {
            return List.of();
        }

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
        if (!runtimeProperties.isPublishedMessageBufferEnabled()) {
            return 0;
        }
        return messages.size();
    }

    public void clear() {
        if (!runtimeProperties.isPublishedMessageBufferEnabled()) {
            return;
        }
        messages.clear();
    }
}
