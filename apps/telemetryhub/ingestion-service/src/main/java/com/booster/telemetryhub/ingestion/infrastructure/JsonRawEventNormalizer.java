package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.common.JsonUtils;
import com.booster.core.webflux.exception.CommonErrorCode;
import com.booster.core.webflux.exception.CoreException;
import com.booster.telemetryhub.contracts.common.EventMetadata;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.devicehealth.DeviceHealthEvent;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;
import com.booster.telemetryhub.ingestion.application.IngestionMessage;
import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.application.RawEventNormalizer;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JsonRawEventNormalizer implements RawEventNormalizer {

    private final IngestionTopicResolver topicResolver;
    private final KafkaEventKeyResolver kafkaEventKeyResolver;

    public JsonRawEventNormalizer(
            IngestionTopicResolver topicResolver,
            KafkaEventKeyResolver kafkaEventKeyResolver
    ) {
        this.topicResolver = topicResolver;
        this.kafkaEventKeyResolver = kafkaEventKeyResolver;
    }

    @Override
    public NormalizedRawEvent normalize(IngestionMessage message) {
        if (message.payload() == null || message.payload().isBlank()) {
            throw new CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "payload must not be blank");
        }

        EventType eventType = topicResolver.resolve(message.topic());

        return switch (eventType) {
            case TELEMETRY -> normalizeTelemetry(message);
            case DEVICE_HEALTH -> normalizeDeviceHealth(message);
            case DRIVING_EVENT -> normalizeDrivingEvent(message);
        };
    }

    private NormalizedRawEvent normalizeTelemetry(IngestionMessage message) {
        TelemetryEvent event = JsonUtils.fromJson(message.payload(), TelemetryEvent.class);
        return normalizeWithMetadata(event.metadata(), EventType.TELEMETRY, message);
    }

    private NormalizedRawEvent normalizeDeviceHealth(IngestionMessage message) {
        DeviceHealthEvent event = JsonUtils.fromJson(message.payload(), DeviceHealthEvent.class);
        return normalizeWithMetadata(event.metadata(), EventType.DEVICE_HEALTH, message);
    }

    private NormalizedRawEvent normalizeDrivingEvent(IngestionMessage message) {
        DrivingEvent event = JsonUtils.fromJson(message.payload(), DrivingEvent.class);
        return normalizeWithMetadata(event.metadata(), EventType.DRIVING_EVENT, message);
    }

    private NormalizedRawEvent normalizeWithMetadata(
            EventMetadata metadata,
            EventType expectedType,
            IngestionMessage message
    ) {
        if (metadata == null) {
            throw new CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "metadata must not be null");
        }
        if (metadata.deviceId() == null || metadata.deviceId().isBlank()) {
            throw new CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "deviceId must not be blank");
        }
        if (metadata.eventId() == null || metadata.eventId().isBlank()) {
            throw new CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "eventId must not be blank");
        }
        if (metadata.eventType() != expectedType) {
            throw new CoreException(
                    CommonErrorCode.INVALID_INPUT_VALUE,
                    "eventType does not match topic. expected=%s actual=%s".formatted(expectedType, metadata.eventType())
            );
        }

        Instant ingestTime = metadata.ingestTime() != null ? metadata.ingestTime() : message.receivedAt();
        Instant eventTime = metadata.eventTime() != null ? metadata.eventTime() : ingestTime;

        return new NormalizedRawEvent(
                metadata.eventType(),
                metadata.eventId(),
                metadata.deviceId(),
                eventTime,
                ingestTime,
                message.topic(),
                kafkaEventKeyResolver.resolve(metadata.eventType(), metadata.eventId(), metadata.deviceId()),
                message.payload()
        );
    }
}
