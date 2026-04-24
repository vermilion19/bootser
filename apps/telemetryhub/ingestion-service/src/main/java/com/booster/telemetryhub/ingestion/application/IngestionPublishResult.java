package com.booster.telemetryhub.ingestion.application;

public record IngestionPublishResult(
        boolean success,
        String target,
        String failureReason
) {
    public static IngestionPublishResult success(String target) {
        return new IngestionPublishResult(true, target, null);
    }

    public static IngestionPublishResult failure(String target, String failureReason) {
        return new IngestionPublishResult(false, target, failureReason);
    }
}
