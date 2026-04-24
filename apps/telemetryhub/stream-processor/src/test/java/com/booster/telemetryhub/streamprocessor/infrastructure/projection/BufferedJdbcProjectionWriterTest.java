package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.application.metrics.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BufferedJdbcProjectionWriterTest {

    private final List<TestBufferedWriter> writersToClose = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (TestBufferedWriter writer : writersToClose) {
            writer.flushOnShutdown();
        }
    }

    @Test
    void shouldCoalesceBufferedEntriesByKeyBeforeFlushing() {
        TestBufferedWriter writer = newWriter(2, Duration.ofHours(1));

        writer.upsertBuffered(new DeviceLastSeenAggregate(
                "device-1",
                "event-1",
                EventType.TELEMETRY,
                Instant.parse("2026-04-24T10:00:00Z"),
                Instant.parse("2026-04-24T10:00:01Z"),
                "topic-a"
        ));
        writer.upsertBuffered(new DeviceLastSeenAggregate(
                "device-1",
                "event-2",
                EventType.TELEMETRY,
                Instant.parse("2026-04-24T10:00:05Z"),
                Instant.parse("2026-04-24T10:00:06Z"),
                "topic-a"
        ));
        writer.upsertBuffered(new DeviceLastSeenAggregate(
                "device-2",
                "event-3",
                EventType.DEVICE_HEALTH,
                Instant.parse("2026-04-24T10:00:07Z"),
                Instant.parse("2026-04-24T10:00:08Z"),
                "topic-b"
        ));

        assertThat(writer.flushedBatches()).hasSize(1);
        assertThat(writer.flushedBatches().getFirst()).hasSize(2);
        assertThat(writer.flushedBatches().getFirst())
                .extracting(DeviceLastSeenAggregate::deviceId)
                .containsExactlyInAnyOrder("device-1", "device-2");
        assertThat(writer.flushedBatches().getFirst())
                .filteredOn(aggregate -> aggregate.deviceId().equals("device-1"))
                .singleElement()
                .extracting(DeviceLastSeenAggregate::lastEventId)
                .isEqualTo("event-2");
    }

    @Test
    void shouldFlushRemainingEntriesOnShutdown() {
        TestBufferedWriter writer = newWriter(10, Duration.ofHours(1));

        writer.upsertBuffered(new DeviceLastSeenAggregate(
                "device-1",
                "event-1",
                EventType.TELEMETRY,
                Instant.parse("2026-04-24T10:00:00Z"),
                Instant.parse("2026-04-24T10:00:01Z"),
                "topic-a"
        ));

        writer.flushOnShutdown();

        assertThat(writer.flushedBatches()).hasSize(1);
        assertThat(writer.flushedBatches().getFirst()).singleElement()
                .extracting(DeviceLastSeenAggregate::lastEventId)
                .isEqualTo("event-1");
    }

    private TestBufferedWriter newWriter(int batchSize, Duration flushInterval) {
        StreamProcessorProperties properties = new StreamProcessorProperties();
        properties.getProjectionBatch().setBatchSize(batchSize);
        properties.getProjectionBatch().setFlushInterval(flushInterval);
        properties.getProjectionBatch().setMaxBufferedEntries(20);

        TestBufferedWriter writer = new TestBufferedWriter(
                new JdbcTemplate(),
                new StreamProcessorMetricsCollector(new SimpleMeterRegistry()),
                properties
        );
        writer.startFlushScheduler();
        writersToClose.add(writer);
        return writer;
    }

    private static final class TestBufferedWriter extends BufferedJdbcProjectionWriter<DeviceLastSeenAggregate, String> {

        private final List<List<DeviceLastSeenAggregate>> flushedBatches = new ArrayList<>();

        private TestBufferedWriter(
                JdbcTemplate jdbcTemplate,
                StreamProcessorMetricsCollector metricsCollector,
                StreamProcessorProperties properties
        ) {
            super(jdbcTemplate, metricsCollector, properties, ProjectionType.DEVICE_LAST_SEEN);
        }

        @Override
        protected String bufferKey(DeviceLastSeenAggregate aggregate) {
            return aggregate.deviceId();
        }

        @Override
        protected DeviceLastSeenAggregate mergeAggregates(DeviceLastSeenAggregate left, DeviceLastSeenAggregate right) {
            return left.merge(right);
        }

        @Override
        protected void batchUpsert(JdbcTemplate jdbcTemplate, List<DeviceLastSeenAggregate> aggregates) {
            flushedBatches.add(List.copyOf(aggregates));
        }

        private List<List<DeviceLastSeenAggregate>> flushedBatches() {
            return flushedBatches;
        }
    }
}
