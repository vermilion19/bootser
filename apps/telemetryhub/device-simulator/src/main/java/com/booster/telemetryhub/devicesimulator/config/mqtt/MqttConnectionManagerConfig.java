package com.booster.telemetryhub.devicesimulator.config.mqtt;

import com.booster.telemetryhub.devicesimulator.config.SimulatorPublisherProperties;
import com.booster.telemetryhub.devicesimulator.infrastructure.mqtt.MqttConnectionManager;
import com.booster.telemetryhub.devicesimulator.infrastructure.mqtt.RealMqttConnectionManager;
import com.booster.telemetryhub.devicesimulator.infrastructure.mqtt.StubMqttConnectionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConnectionManagerConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "telemetryhub.simulator.publisher.mqtt",
            name = "real-client-enabled",
            havingValue = "true"
    )
    public MqttConnectionManager realMqttConnectionManager(SimulatorPublisherProperties publisherProperties) {
        return new RealMqttConnectionManager(publisherProperties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "telemetryhub.simulator.publisher.mqtt",
            name = "real-client-enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public MqttConnectionManager stubMqttConnectionManager(SimulatorPublisherProperties publisherProperties) {
        return new StubMqttConnectionManager(publisherProperties);
    }
}
