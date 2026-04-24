package com.booster.telemetryhub.ingestion.application.publisher;

import com.booster.telemetryhub.ingestion.application.normalize.NormalizedRawEvent;

public interface IngestionPublisher {

    IngestionPublishResult publish(NormalizedRawEvent event);
}
