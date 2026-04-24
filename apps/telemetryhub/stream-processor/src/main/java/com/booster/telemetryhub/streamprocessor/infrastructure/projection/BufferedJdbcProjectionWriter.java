package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.telemetryhub.streamprocessor.application.metrics.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

abstract class BufferedJdbcProjectionWriter<T, K> {

    private static final Logger log = LoggerFactory.getLogger(BufferedJdbcProjectionWriter.class);

    private final Object bufferMonitor = new Object();
    private final Map<K, T> bufferedAggregates = new LinkedHashMap<>();
    private final JdbcTemplate jdbcTemplate;
    private final StreamProcessorMetricsCollector metricsCollector;
    private final StreamProcessorProperties.ProjectionBatch projectionBatchProperties;
    private final ProjectionType projectionType;
    private ScheduledExecutorService flushExecutor;

    protected BufferedJdbcProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector,
            StreamProcessorProperties properties,
            ProjectionType projectionType
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricsCollector = metricsCollector;
        this.projectionBatchProperties = properties.getProjectionBatch();
        this.projectionType = projectionType;
    }

    @PostConstruct
    void startFlushScheduler() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "projection-flush-" + projectionType.name().toLowerCase());
            thread.setDaemon(true);
            return thread;
        };

        flushExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        long flushIntervalMillis = Math.max(1L, projectionBatchProperties.getFlushInterval().toMillis());
        flushExecutor.scheduleAtFixedRate(
                () -> flushBufferedAggregates(false),
                flushIntervalMillis,
                flushIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void flushOnShutdown() {
        try {
            flushBufferedAggregates(false);
        } finally {
            if (flushExecutor != null) {
                flushExecutor.shutdown();
                try {
                    flushExecutor.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    protected final void upsertBuffered(T aggregate) {
        List<T> aggregatesToFlush = null;

        synchronized (bufferMonitor) {
            bufferedAggregates.merge(bufferKey(aggregate), aggregate, this::mergeAggregates);
            if (bufferedAggregates.size() >= projectionBatchProperties.getBatchSize()
                    || bufferedAggregates.size() >= projectionBatchProperties.getMaxBufferedEntries()) {
                aggregatesToFlush = drainBufferLocked();
            }
        }

        if (aggregatesToFlush != null && !aggregatesToFlush.isEmpty()) {
            flushAggregates(aggregatesToFlush, true);
        }
    }

    private void flushBufferedAggregates(boolean throwOnFailure) {
        List<T> aggregatesToFlush;
        synchronized (bufferMonitor) {
            if (bufferedAggregates.isEmpty()) {
                return;
            }
            aggregatesToFlush = drainBufferLocked();
        }

        flushAggregates(aggregatesToFlush, throwOnFailure);
    }

    private List<T> drainBufferLocked() {
        List<T> drained = new ArrayList<>(bufferedAggregates.values());
        bufferedAggregates.clear();
        return drained;
    }

    private void flushAggregates(List<T> aggregates, boolean throwOnFailure) {
        try {
            batchUpsert(jdbcTemplate, aggregates);
            metricsCollector.recordProjectionWriteSuccess(projectionType, aggregates.size());
        } catch (RuntimeException exception) {
            requeueAggregates(aggregates);
            metricsCollector.recordProjectionWriteFailure(projectionType, exception, aggregates.size());
            if (throwOnFailure) {
                throw exception;
            }
            log.warn("Projection batch flush failed for {} with {} buffered aggregates", projectionType, aggregates.size(), exception);
        }
    }

    private void requeueAggregates(List<T> aggregates) {
        synchronized (bufferMonitor) {
            for (T aggregate : aggregates) {
                bufferedAggregates.merge(bufferKey(aggregate), aggregate, this::mergeAggregates);
            }

            if (bufferedAggregates.size() > projectionBatchProperties.getMaxBufferedEntries()) {
                log.warn(
                        "Projection buffer for {} grew beyond maxBufferedEntries={} after requeue; currentSize={}",
                        projectionType,
                        projectionBatchProperties.getMaxBufferedEntries(),
                        bufferedAggregates.size()
                );
            }
        }
    }

    protected abstract K bufferKey(T aggregate);

    protected abstract T mergeAggregates(T left, T right);

    protected abstract void batchUpsert(JdbcTemplate jdbcTemplate, List<T> aggregates);
}
