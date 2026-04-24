package com.booster.telemetryhub.ingestion.application;

public interface RawEventNormalizer {

    NormalizedRawEvent normalize(IngestionMessage message);
}
