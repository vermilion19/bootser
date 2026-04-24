package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.common.JsonUtils;
import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.BackfillSourceReader;
import com.booster.telemetryhub.batchbackfill.config.BatchBackfillProperties;
import com.booster.telemetryhub.batchbackfill.domain.BackfillSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class FileBackfillSourceReader implements BackfillSourceReader {

    private static final Logger log = LoggerFactory.getLogger(FileBackfillSourceReader.class);

    private final BatchBackfillProperties properties;

    public FileBackfillSourceReader(BatchBackfillProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<BackfillRawEvent> read(BackfillPlan plan) {
        Path sourcePath = resolveSourcePath(plan.sourceType());
        if (!Files.exists(sourcePath)) {
            throw new IllegalStateException("Backfill source file does not exist: " + sourcePath);
        }

        try (var lines = Files.lines(sourcePath)) {
            List<BackfillRawEvent> events = lines
                    .filter(line -> !line.isBlank())
                    .map(line -> JsonUtils.fromJson(line, FileBackfillRawEventRecord.class))
                    .map(record -> new BackfillRawEvent(
                            record.eventType(),
                            record.eventId(),
                            record.deviceId(),
                            record.eventTime(),
                            record.payload()
                    ))
                    .filter(event -> !event.eventTime().isBefore(plan.from()))
                    .filter(event -> !event.eventTime().isAfter(plan.to()))
                    .toList();

            log.info(
                    "Read backfill source file: sourceType={}, path={}, matchedEvents={}",
                    plan.sourceType(),
                    sourcePath,
                    events.size()
            );
            return events;
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
