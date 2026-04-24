package com.booster.telemetryhub.streamprocessor.infrastructure;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.stereotype.Component;

@Component
public class JsonSerdeFactory {

    public <T> Serde<T> serde(Class<T> targetType) {
        return Serdes.serdeFrom(
                new JsonPojoSerializer<>(),
                new JsonPojoDeserializer<>(targetType)
        );
    }
}
