package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.common.JsonUtils;
import com.booster.telemetryhub.ingestion.application.IngestionPublishResult;
import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.config.IngestionPublisherProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KafkaIngestionPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaIngestionPublisher.class);

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final IngestionPublisherProperties publisherProperties;
    private final AtomicReference<KafkaPublishSnapshot> snapshotRef;

    public KafkaIngestionPublisher(
            KafkaTemplate<String, String> stringKafkaTemplate,
            IngestionPublisherProperties publisherProperties
    ) {
        this.stringKafkaTemplate = stringKafkaTemplate;
        this.publisherProperties = publisherProperties;
        this.snapshotRef = new AtomicReference<>(
                KafkaPublishSnapshot.initial(
                        publisherProperties.getKafka().isEnabled(),
                        publisherProperties.getKafka().getRawTopic()
                )
        );
    }

    public IngestionPublishResult publish(NormalizedRawEvent event) {
        if (!publisherProperties.getKafka().isEnabled()) {
            recordFailure("kafka.enabled=false");
            log.warn(
                    "Kafka publisher mode selected but kafka.enabled=false. topic={}, eventId={}, deviceId={}",
                    publisherProperties.getKafka().getRawTopic(),
                    event.eventId(),
                    event.deviceId()
            );
            return IngestionPublishResult.failure("KAFKA", "kafka.enabled=false");
        }

        try {
            CompletableFuture<?> sendFuture = stringKafkaTemplate.send(
                    publisherProperties.getKafka().getRawTopic(),
                    event.kafkaKey(),
                    JsonUtils.toJson(event)
            );
            sendFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    recordFailure(throwable.getMessage());
                    log.warn(
                            "Kafka publish failed asynchronously. topic={}, eventId={}, deviceId={}, reason={}",
                            publisherProperties.getKafka().getRawTopic(),
                            event.eventId(),
                            event.deviceId(),
                            throwable.getMessage()
                    );
                    return;
                }
                recordSuccess();
            });
            return IngestionPublishResult.success("KAFKA");
        } catch (RuntimeException exception) {
            recordFailure(exception.getMessage());
            return IngestionPublishResult.failure("KAFKA", exception.getMessage());
        }
    }

    public KafkaPublishSnapshot snapshot() {
        return snapshotRef.get();
    }

    private void recordSuccess() {
        snapshotRef.updateAndGet(current -> new KafkaPublishSnapshot(
                true,
                publisherProperties.getKafka().getRawTopic(),
                current.totalPublished() + 1,
                current.totalFailed(),
                Instant.now(),
                null
        ));
    }

    private void recordFailure(String reason) {
        snapshotRef.updateAndGet(current -> new KafkaPublishSnapshot(
                publisherProperties.getKafka().isEnabled(),
                publisherProperties.getKafka().getRawTopic(),
                current.totalPublished(),
                current.totalFailed() + 1,
                current.lastPublishedAt(),
                reason
        ));
    }
}
