package com.booster.telemetryhub.streamprocessor.infrastructure.topology;

import com.booster.telemetryhub.streamprocessor.domain.RawEventMessage;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LateEventPolicySupport {

    public KStream<String, RawEventMessage> retainWithinGrace(
            KStream<String, RawEventMessage> sourceStream,
            Duration lateEventGrace
    ) {
        return sourceStream.filter((key, event) -> isWithinGrace(event, lateEventGrace));
    }

    boolean isWithinGrace(RawEventMessage event, Duration lateEventGrace) {
        if (event == null || event.eventTime() == null || event.ingestTime() == null) {
            return true;
        }

        Duration delay = Duration.between(event.eventTime(), event.ingestTime());
        if (delay.isNegative()) {
            return true;
        }
        return delay.compareTo(lateEventGrace) <= 0;
    }
}
