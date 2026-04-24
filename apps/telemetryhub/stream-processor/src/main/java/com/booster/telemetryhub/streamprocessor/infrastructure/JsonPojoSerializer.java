package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.common.JsonUtils;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.charset.StandardCharsets;

public class JsonPojoSerializer<T> implements Serializer<T> {

    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        return JsonUtils.toJson(data).getBytes(StandardCharsets.UTF_8);
    }
}
