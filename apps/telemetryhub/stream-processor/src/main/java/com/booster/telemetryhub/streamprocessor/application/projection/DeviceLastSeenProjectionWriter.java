package com.booster.telemetryhub.streamprocessor.application.projection;

import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;

public interface DeviceLastSeenProjectionWriter {

    void upsert(DeviceLastSeenAggregate aggregate);
}
