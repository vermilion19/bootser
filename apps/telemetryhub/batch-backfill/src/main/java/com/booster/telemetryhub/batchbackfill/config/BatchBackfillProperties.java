package com.booster.telemetryhub.batchbackfill.config;

import com.booster.telemetryhub.batchbackfill.domain.BackfillOverwriteMode;
import com.booster.telemetryhub.batchbackfill.domain.BackfillSourceType;
import com.booster.telemetryhub.batchbackfill.domain.BackfillTarget;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "telemetryhub.backfill")
public class BatchBackfillProperties {

    private Duration defaultLookback = Duration.ofDays(1);
    private int chunkSize = 500;
    private boolean dryRun = true;
    private BackfillSourceType sourceType = BackfillSourceType.RAW_TOPIC_EXPORT;
    private BackfillOverwriteMode overwriteMode = BackfillOverwriteMode.MERGE;
    private double heatmapGridSize = 0.01d;
    private String rawTopicExportPath = "data/telemetryhub/raw-topic-export.ndjson";
    private String rawArchivePath = "data/telemetryhub/raw-archive.ndjson";
    private boolean fallbackToStubWhenSourceMissing = true;
    private List<BackfillTarget> defaultTargets = List.of(
            BackfillTarget.DEVICE_LAST_SEEN,
            BackfillTarget.EVENTS_PER_MINUTE,
            BackfillTarget.DRIVING_EVENT_COUNTER,
            BackfillTarget.REGION_HEATMAP
    );

    public Duration getDefaultLookback() {
        return defaultLookback;
    }

    public void setDefaultLookback(Duration defaultLookback) {
        this.defaultLookback = defaultLookback;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public BackfillSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(BackfillSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public BackfillOverwriteMode getOverwriteMode() {
        return overwriteMode;
    }

    public void setOverwriteMode(BackfillOverwriteMode overwriteMode) {
        this.overwriteMode = overwriteMode;
    }

    public String getRawTopicExportPath() {
        return rawTopicExportPath;
    }

    public void setRawTopicExportPath(String rawTopicExportPath) {
        this.rawTopicExportPath = rawTopicExportPath;
    }

    public double getHeatmapGridSize() {
        return heatmapGridSize;
    }

    public void setHeatmapGridSize(double heatmapGridSize) {
        this.heatmapGridSize = heatmapGridSize;
    }

    public String getRawArchivePath() {
        return rawArchivePath;
    }

    public void setRawArchivePath(String rawArchivePath) {
        this.rawArchivePath = rawArchivePath;
    }

    public boolean isFallbackToStubWhenSourceMissing() {
        return fallbackToStubWhenSourceMissing;
    }

    public void setFallbackToStubWhenSourceMissing(boolean fallbackToStubWhenSourceMissing) {
        this.fallbackToStubWhenSourceMissing = fallbackToStubWhenSourceMissing;
    }

    public List<BackfillTarget> getDefaultTargets() {
        return defaultTargets;
    }

    public void setDefaultTargets(List<BackfillTarget> defaultTargets) {
        this.defaultTargets = defaultTargets;
    }
}
