package com.booster.telemetryhub.batchbackfill.infrastructure.source;

import com.booster.common.JsonUtils;
import com.booster.telemetryhub.batchbackfill.application.io.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.io.BackfillSourceReader;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.config.BatchBackfillProperties;
import com.booster.telemetryhub.batchbackfill.domain.BackfillSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class FileBackfillSourceReader implements BackfillSourceReader {

    private static final Logger log = LoggerFactory.getLogger(FileBackfillSourceReader.class);

    private final BatchBackfillProperties properties;

    public FileBackfillSourceReader(BatchBackfillProperties properties) {
        this.properties = properties;
    }

    @Override
    public void readChunks(BackfillPlan plan, Consumer<List<BackfillRawEvent>> chunkConsumer) {
        Path sourcePath = resolveSourcePath(plan.sourceType());
        if (!Files.exists(sourcePath)) {
            throw new IllegalStateException("Backfill source file does not exist: " + sourcePath);
        }

        try (var lines = Files.lines(sourcePath)) {
            List<BackfillRawEvent> chunk = new ArrayList<>(plan.chunkSize());
            long matchedEvents = 0;
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) {
                    continue;
                }

                FileBackfillRawEventRecord record = JsonUtils.fromJson(line, FileBackfillRawEventRecord.class);
                BackfillRawEvent event = new BackfillRawEvent(
                        record.eventType(),
                        record.eventId(),
                        record.deviceId(),
                        record.eventTime(),
                        record.ingestTime() != null ? record.ingestTime() : record.eventTime(),
                        record.payload()
                );
                if (event.eventTime().isBefore(plan.from()) || event.eventTime().isAfter(plan.to())) {
                    continue;
                }

                chunk.add(event);
                matchedEvents++;
                if (chunk.size() >= plan.chunkSize()) {
                    chunkConsumer.accept(List.copyOf(chunk));
                    chunk.clear();
                }
            }

            if (!chunk.isEmpty()) {
                chunkConsumer.accept(List.copyOf(chunk));
            }

            log.info(
                    "Read backfill source file in chunks: sourceType={}, path={}, matchedEvents={}, chunkSize={}",
                    plan.sourceType(),
                    sourcePath,
                    matchedEvents,
                    plan.chunkSize()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read backfill source file: " + sourcePath, exception);
        }
    }

    private Path resolveSourcePath(BackfillSourceType sourceType) {
        return switch (sourceType) {
            case RAW_TOPIC_EXPORT -> Path.of(properties.getRawTopicExportPath());
            case RAW_ARCHIVE -> Path.of(properties.getRawArchivePath());
        };
    }
}
