package com.booster.telemetryhub.ingestion.application;

public interface IngestionPublisher {

    void publish(NormalizedRawEvent event);
}
