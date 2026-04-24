package com.booster.telemetryhub.devicesimulator.infrastructure;

import com.booster.telemetryhub.devicesimulator.config.SimulatorRuntimeProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

@Component
public class InMemoryPublishedMessageStore {

    private final SimulatorRuntimeProperties runtimeProperties;
    private final LinkedBlockingDeque<MemoryPublishedMessage> messages;

    public InMemoryPublishedMessageStore(SimulatorRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
        this.messages = new LinkedBlockingDeque<>(runtimeProperties.getMaxPublishedMessages());
    }

    public synchronized void append(List<MemoryPublishedMessage> batch) {
        if (!runtimeProperties.isPublishedMessageBufferEnabled()) {
            return;
        }

        for (MemoryPublishedMessage message : batch) {
            if (!messages.offerFirst(message)) {
                messages.pollLast();
                messages.offerFirst(message);
            }
        }
    }

    public synchronized List<MemoryPublishedMessage> recent(int limit) {
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

    public synchronized int size() {
        if (!runtimeProperties.isPublishedMessageBufferEnabled()) {
            return 0;
        }
        return messages.size();
    }

    public synchronized void clear() {
        if (!runtimeProperties.isPublishedMessageBufferEnabled()) {
            return;
        }
        messages.clear();
    }
}
