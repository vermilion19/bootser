package com.booster.telemetryhub.ingestion.web.dto;

import com.booster.telemetryhub.ingestion.application.IngestionMetricsSnapshot;
import com.booster.telemetryhub.ingestion.infrastructure.KafkaPublishSnapshot;

import java.time.Instant;

public record IngestionMetricsResponse(
        long totalReceived,
        long totalPublished,
        long totalFailed,
        Instant lastReceivedAt,
        Instant lastPublishedAt,
        String lastFailureStage,
        String lastFailureReason,
        KafkaPublishResponse kafkaPublish
) {
    public static IngestionMetricsResponse from(IngestionMetricsSnapshot snapshot, KafkaPublishSnapshot kafkaSnapshot) {
        return new IngestionMetricsResponse(
                snapshot.totalReceived(),
                snapshot.totalPublished(),
                snapshot.totalFailed(),
                snapshot.lastReceivedAt(),
                snapshot.lastPublishedAt(),
                snapshot.lastFailureStage(),
                snapshot.lastFailureReason(),
                KafkaPublishResponse.from(kafkaSnapshot)
        );
    }

    public record KafkaPublishResponse(
            boolean enabled,
            String rawTopic,
            long totalPublished,
            long totalFailed,
            Instant lastPublishedAt,
            String lastFailureReason
    ) {
        static KafkaPublishResponse from(KafkaPublishSnapshot snapshot) {
            return new KafkaPublishResponse(
                    snapshot.enabled(),
                    snapshot.rawTopic(),
                    snapshot.totalPublished(),
                    snapshot.totalFailed(),
                    snapshot.lastPublishedAt(),
                    snapshot.lastFailureReason()
            );
        }
    }
}
