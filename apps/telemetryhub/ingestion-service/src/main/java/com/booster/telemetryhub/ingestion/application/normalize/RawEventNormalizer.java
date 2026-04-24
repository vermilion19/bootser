package com.booster.telemetryhub.ingestion.application.normalize;

import com.booster.telemetryhub.ingestion.application.ingest.IngestionMessage;

public interface RawEventNormalizer {

    NormalizedRawEvent normalize(IngestionMessage message);
}
