package com.booster.telemetryhub.streamprocessor.application;

import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;

public interface DeviceLastSeenProjectionWriter {

    void upsert(DeviceLastSeenAggregate aggregate);
}
