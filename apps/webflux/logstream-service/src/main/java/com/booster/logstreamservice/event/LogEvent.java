package com.booster.logstreamservice.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogEvent {
    private String logId;
    private String payload;
    private long timestamp;

    public void set(String logId, String payload, long timestamp) {
        this.logId = logId;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    // 올바른 패키지 경로 (com.lmax)
    public static final com.lmax.disruptor.EventFactory<LogEvent> FACTORY = LogEvent::new;
}