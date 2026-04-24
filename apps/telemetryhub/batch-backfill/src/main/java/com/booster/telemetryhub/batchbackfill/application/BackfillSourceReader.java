package com.booster.telemetryhub.batchbackfill.application;

import java.util.List;

public interface BackfillSourceReader {

    List<BackfillRawEvent> read(BackfillPlan plan);
}
