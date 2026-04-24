package com.booster.telemetryhub.streamprocessor.infrastructure.serde;

import com.booster.common.JsonUtils;
import org.apache.kafka.common.serialization.Deserializer;

import java.nio.charset.StandardCharsets;

public class JsonPojoDeserializer<T> implements Deserializer<T> {

    private final Class<T> targetType;

    public JsonPojoDeserializer(Class<T> targetType) {
        this.targetType = targetType;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return JsonUtils.fromJson(new String(data, StandardCharsets.UTF_8), targetType);
    }
}
