package com.booster.telemetryhub.streamprocessor.application.projection;

import com.booster.telemetryhub.streamprocessor.domain.EventsPerMinuteAggregate;

public interface EventsPerMinuteProjectionWriter {

    void upsert(EventsPerMinuteAggregate aggregate);
}
