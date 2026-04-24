package com.booster.telemetryhub.batchbackfill.application.io;

import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import java.util.List;
import java.util.function.Consumer;

public interface BackfillSourceReader {

    void readChunks(BackfillPlan plan, Consumer<List<BackfillRawEvent>> chunkConsumer);
}
