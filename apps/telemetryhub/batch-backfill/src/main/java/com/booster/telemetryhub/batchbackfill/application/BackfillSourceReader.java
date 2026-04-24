package com.booster.telemetryhub.batchbackfill.application;

import java.util.List;
import java.util.function.Consumer;

public interface BackfillSourceReader {

    void readChunks(BackfillPlan plan, Consumer<List<BackfillRawEvent>> chunkConsumer);
}
