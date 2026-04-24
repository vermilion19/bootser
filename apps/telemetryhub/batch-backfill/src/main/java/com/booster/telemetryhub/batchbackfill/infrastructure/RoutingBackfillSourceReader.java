package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.BackfillSourceReader;
import com.booster.telemetryhub.batchbackfill.config.BatchBackfillProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoutingBackfillSourceReader implements BackfillSourceReader {

    private static final Logger log = LoggerFactory.getLogger(RoutingBackfillSourceReader.class);

    private final FileBackfillSourceReader fileBackfillSourceReader;
    private final StubBackfillSourceReader stubBackfillSourceReader;
    private final BatchBackfillProperties properties;

    public RoutingBackfillSourceReader(
            FileBackfillSourceReader fileBackfillSourceReader,
            StubBackfillSourceReader stubBackfillSourceReader,
            BatchBackfillProperties properties
    ) {
        this.fileBackfillSourceReader = fileBackfillSourceReader;
        this.stubBackfillSourceReader = stubBackfillSourceReader;
        this.properties = properties;
    }

    @Override
    public List<BackfillRawEvent> read(BackfillPlan plan) {
        try {
            return fileBackfillSourceReader.read(plan);
        } catch (RuntimeException exception) {
            if (!properties.isFallbackToStubWhenSourceMissing()) {
                throw exception;
            }

            log.warn(
                    "Backfill source reader fallback activated: sourceType={}, reason={}",
                    plan.sourceType(),
                    exception.getMessage()
            );
            return stubBackfillSourceReader.read(plan);
        }
    }
}
