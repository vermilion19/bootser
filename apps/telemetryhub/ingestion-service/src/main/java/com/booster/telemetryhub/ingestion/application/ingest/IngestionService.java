package com.booster.telemetryhub.ingestion.application.ingest;

import com.booster.telemetryhub.ingestion.application.failure.IngestionFailureStage;
import com.booster.telemetryhub.ingestion.application.metrics.IngestionMetricsSnapshot;
import com.booster.telemetryhub.ingestion.application.normalize.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.application.normalize.RawEventNormalizer;
import com.booster.telemetryhub.ingestion.application.publisher.IngestionPublishResult;
import com.booster.telemetryhub.ingestion.application.publisher.IngestionPublisher;
import com.booster.telemetryhub.ingestion.infrastructure.store.InMemoryNormalizedRawEventStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IngestionService {

    private final RawEventNormalizer rawEventNormalizer;
    private final IngestionPublisher ingestionPublisher;
    private final InMemoryNormalizedRawEventStore inMemoryNormalizedRawEventStore;
    private final AtomicReference<IngestionMetricsSnapshot> metricsRef = new AtomicReference<>(IngestionMetricsSnapshot.empty());

    public IngestionService(
            RawEventNormalizer rawEventNormalizer,
            IngestionPublisher ingestionPublisher,
            InMemoryNormalizedRawEventStore inMemoryNormalizedRawEventStore
    ) {
        this.rawEventNormalizer = rawEventNormalizer;
        this.ingestionPublisher = ingestionPublisher;
        this.inMemoryNormalizedRawEventStore = inMemoryNormalizedRawEventStore;
    }

    public NormalizedRawEvent ingest(IngestionMessage message) {
        markReceived(message.receivedAt());
        try {
            NormalizedRawEvent normalized = rawEventNormalizer.normalize(message);
            IngestionPublishResult publishResult = ingestionPublisher.publish(normalized);
            if (!publishResult.success()) {
                markFailed(message.receivedAt(), IngestionFailureStage.PUBLISH, publishResult.failureReason());
                throw new IllegalStateException(publishResult.failureReason());
            }
            markPublished(normalized.ingestTime());
            return normalized;
        } catch (RuntimeException exception) {
            if (!(exception instanceof IllegalStateException)) {
                markFailed(message.receivedAt(), IngestionFailureStage.NORMALIZE, exception.getMessage());
            }
            throw exception;
        }
    }

    public IngestionMetricsSnapshot metrics() {
        return metricsRef.get();
    }

    public List<NormalizedRawEvent> recentEvents(int limit) {
        return inMemoryNormalizedRawEventStore.recent(limit);
    }

    public void clearRecentEvents() {
        inMemoryNormalizedRawEventStore.clear();
    }

    private void markReceived(Instant receivedAt) {
        metricsRef.updateAndGet(current -> new IngestionMetricsSnapshot(
                current.totalReceived() + 1,
                current.totalPublished(),
                current.totalFailed(),
                receivedAt,
                current.lastPublishedAt(),
                current.lastFailureStage(),
                current.lastFailureReason()
        ));
    }

    private void markPublished(Instant publishedAt) {
        metricsRef.updateAndGet(current -> new IngestionMetricsSnapshot(
                current.totalReceived(),
                current.totalPublished() + 1,
                current.totalFailed(),
                current.lastReceivedAt(),
                publishedAt,
                current.lastFailureStage(),
                current.lastFailureReason()
        ));
    }

    private void markFailed(Instant receivedAt, IngestionFailureStage stage, String reason) {
        metricsRef.updateAndGet(current -> new IngestionMetricsSnapshot(
                current.totalReceived(),
                current.totalPublished(),
                current.totalFailed() + 1,
                receivedAt,
                current.lastPublishedAt(),
                stage.name(),
                reason
        ));
    }
}
