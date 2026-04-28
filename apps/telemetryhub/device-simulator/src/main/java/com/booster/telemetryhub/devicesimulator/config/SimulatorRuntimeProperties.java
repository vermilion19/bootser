package com.booster.telemetryhub.devicesimulator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "telemetryhub.simulator.runtime")
public class SimulatorRuntimeProperties {

    private boolean publishedMessageBufferEnabled = true;
    private int maxPublishedMessages = 500;
}
