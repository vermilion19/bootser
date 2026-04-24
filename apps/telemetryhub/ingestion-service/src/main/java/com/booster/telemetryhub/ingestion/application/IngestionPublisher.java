package com.booster.telemetryhub.ingestion.application;

public interface IngestionPublisher {

    IngestionPublishResult publish(NormalizedRawEvent event);
}
