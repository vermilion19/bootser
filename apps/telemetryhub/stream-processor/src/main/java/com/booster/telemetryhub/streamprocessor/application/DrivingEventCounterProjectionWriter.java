package com.booster.telemetryhub.streamprocessor.application;

import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterAggregate;

public interface DrivingEventCounterProjectionWriter {

    void upsert(DrivingEventCounterAggregate aggregate);
}
